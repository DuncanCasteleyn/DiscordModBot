package be.duncanc.discordmodbot.member.gate

import be.duncanc.discordmodbot.discord.SlashCommand
import be.duncanc.discordmodbot.member.gate.persistence.MemberGateQuestion
import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.components.actionrow.ActionRow
import net.dv8tion.jda.api.components.buttons.Button
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent
import net.dv8tion.jda.api.hooks.ListenerAdapter
import net.dv8tion.jda.api.interactions.InteractionContextType
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions
import net.dv8tion.jda.api.interactions.commands.build.Commands
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData
import net.dv8tion.jda.api.utils.MarkdownUtil
import org.springframework.stereotype.Component

@Component
class MemberGateReviewCommand(
    private val reviewManager: MemberGateReviewManager,
    private val reviewSessionRegistry: MemberGateReviewSessionRegistry
) : ListenerAdapter(), SlashCommand {
    companion object {
        private const val COMMAND = "review"
        private const val BUTTON_PREFIX = "member-gate-review:"
        private const val APPROVE_ACTION = "approve"
        private const val REJECT_ACTION = "reject"
        private const val MANUAL_ACTION = "manual"

        private data class ReviewButtonAction(
            val action: String,
            val expectedUserId: Long,
            val expectedQueuedAt: Long
        )
    }

    override fun getCommandsData(): List<SlashCommandData> {
        return listOf(
            Commands.slash(COMMAND, "Review pending member gate applications")
                .setContexts(InteractionContextType.GUILD)
                .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.KICK_MEMBERS))
        )
    }

    override fun onSlashCommandInteraction(event: SlashCommandInteractionEvent) {
        val guild = event.guild
        if (event.name != COMMAND || guild == null) {
            return
        }

        if (event.member?.hasPermission(Permission.KICK_MEMBERS) != true) {
            event.reply("You need the kick members permission to use this command.").setEphemeral(true).queue()
            return
        }

        val session = reviewManager.createSession(guild.idLong)
        if (session == null) {
            event.reply("Nobody is currently waiting for approval.").setEphemeral(true).queue()
            return
        }

        val pendingQuestion = resolveCurrentQuestion(guild.idLong, session)
        if (pendingQuestion == null) {
            reviewSessionRegistry.forget(guild.idLong, event.user.idLong)
            event.reply("Nobody is currently waiting for approval.").setEphemeral(true).queue()
            return
        }

        reviewSessionRegistry.remember(guild.idLong, event.user.idLong, session)

        event.reply(buildReviewMessage(guild, session, pendingQuestion))
            .setEphemeral(true)
            .addComponents(ActionRow.of(buildButtons(pendingQuestion)))
            .queue()
    }

    override fun onButtonInteraction(event: ButtonInteractionEvent) {
        if (!event.componentId.startsWith(BUTTON_PREFIX)) {
            return
        }

        val guild = event.guild
        if (guild == null || event.member?.hasPermission(Permission.KICK_MEMBERS) != true) {
            event.reply("You need the kick members permission to use this command.").setEphemeral(true).queue()
            return
        }

        val session = reviewSessionRegistry.get(guild.idLong, event.user.idLong)
        if (session == null) {
            event.reply("This review session expired. Run `/review` again.").setEphemeral(true).queue()
            return
        }

        val buttonAction = parseButtonAction(event.componentId)
        if (buttonAction == null) {
            event.reply("This review action is no longer available. Run `/review` again.")
                .setEphemeral(true)
                .queue()
            return
        }

        val currentUserId = session.getCurrentUserId()
        if (currentUserId == null) {
            reviewSessionRegistry.forget(guild.idLong, event.user.idLong)
            event.editMessage("This review session has already finished.").setComponents(emptyList()).queue()
            return
        }

        if (buttonAction.expectedUserId != currentUserId) {
            event.reply("This review message is out of date. Run `/review` again.")
                .setEphemeral(true)
                .queue()
            return
        }

        val currentQuestion = reviewManager.getPendingQuestion(guild.idLong, currentUserId)
        if (currentQuestion == null || currentQuestion.queuedAt != buttonAction.expectedQueuedAt) {
            event.reply("This review message is out of date. Run `/review` again.")
                .setEphemeral(true)
                .queue()
            return
        }

        val feedback = when (buttonAction.action) {
            APPROVE_ACTION -> {
                val result = reviewManager.approve(guild, event.jda, currentUserId)
                session.advanceAfterReview()
                result
            }

            REJECT_ACTION -> {
                val result = reviewManager.reject(guild, event.jda, currentUserId)
                session.advanceAfterReview()
                result
            }

            MANUAL_ACTION -> {
                val result = reviewManager.reject(guild, event.jda, currentUserId, manualAction = true)
                session.advanceAfterReview()
                result
            }

            else -> {
                event.reply("This review action is no longer available. Run `/review` again.")
                    .setEphemeral(true)
                    .queue()
                return
            }
        }

        val pendingQuestion = resolveCurrentQuestion(guild.idLong, session)
        if (pendingQuestion == null) {
            reviewSessionRegistry.forget(guild.idLong, event.user.idLong)
            event.editMessage(buildCompletionMessage(feedback)).setComponents(emptyList()).queue()
            return
        }

        reviewSessionRegistry.remember(guild.idLong, event.user.idLong, session)

        event.editMessage(buildReviewMessage(guild, session, pendingQuestion, feedback))
            .setComponents(ActionRow.of(buildButtons(pendingQuestion)))
            .queue()
    }

    private fun parseButtonAction(componentId: String): ReviewButtonAction? {
        if (!componentId.startsWith(BUTTON_PREFIX)) {
            return null
        }

        val segments = componentId.removePrefix(BUTTON_PREFIX).split(":", limit = 3)
        if (segments.size != 3) {
            return null
        }

        val expectedUserId = segments[1].toLongOrNull() ?: return null
        val expectedQueuedAt = segments[2].toLongOrNull() ?: return null
        return ReviewButtonAction(segments[0], expectedUserId, expectedQueuedAt)
    }

    private fun resolveCurrentQuestion(guildId: Long, session: MemberGateReviewSession): MemberGateQuestion? {
        while (true) {
            val currentUserId = session.getCurrentUserId() ?: return null
            val question = reviewManager.getPendingQuestion(guildId, currentUserId)
            if (question != null) {
                return question
            }

            session.advancePastResolvedCurrent()
        }
    }

    private fun buildReviewMessage(
        guild: Guild,
        session: MemberGateReviewSession,
        question: MemberGateQuestion,
        feedback: String? = null
    ): String {
        val member = guild.getMemberById(question.userId)
        val applicant = member?.user?.asMention ?: "<@${question.userId}>"
        val prefix = feedback?.let { "$it\n\n" } ?: ""
        val oldestLine = if (session.isCurrentOldest()) {
            "Reviewing the oldest pending applicant first.\n\n"
        } else {
            "Continuing with the next pending applicant.\n\n"
        }

        return prefix +
                oldestLine +
                "Applicant: $applicant (`${question.userId}`)\n" +
                MarkdownUtil.codeblock("text", "${question.question}\n${question.answer}") +
                "\nChoose `Approve`, `Reject`, or `Manual action`."
    }

    private fun buildCompletionMessage(feedback: String): String {
        return "$feedback\n\nThere are no more pending applicants in the queue."
    }

    private fun buildButtons(question: MemberGateQuestion) = listOf(
        Button.success("$BUTTON_PREFIX$APPROVE_ACTION:${question.userId}:${question.queuedAt}", "Approve"),
        Button.danger("$BUTTON_PREFIX$REJECT_ACTION:${question.userId}:${question.queuedAt}", "Reject"),
        Button.primary("$BUTTON_PREFIX$MANUAL_ACTION:${question.userId}:${question.queuedAt}", "Manual Action")
    )
}
