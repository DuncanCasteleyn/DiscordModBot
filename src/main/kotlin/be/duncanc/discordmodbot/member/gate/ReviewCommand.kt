package be.duncanc.discordmodbot.member.gate

import be.duncanc.discordmodbot.discord.SlashCommand
import be.duncanc.discordmodbot.discord.nicknameAndUsername
import be.duncanc.discordmodbot.logging.GuildLogger
import be.duncanc.discordmodbot.member.gate.persistence.MemberGateQuestion
import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.components.actionrow.ActionRow
import net.dv8tion.jda.api.components.buttons.Button
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.Member
import net.dv8tion.jda.api.entities.User
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent
import net.dv8tion.jda.api.hooks.ListenerAdapter
import net.dv8tion.jda.api.interactions.InteractionContextType
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions
import net.dv8tion.jda.api.interactions.commands.build.Commands
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData
import net.dv8tion.jda.api.utils.MarkdownUtil
import net.dv8tion.jda.api.utils.TimeFormat
import org.springframework.stereotype.Component
import java.awt.Color
import java.time.Instant

@Component
class ReviewCommand(
    private val reviewManager: ReviewManager,
    private val reviewSessionRegistry: ReviewSessionRegistry,
    private val guildLogger: GuildLogger
) : ListenerAdapter(), SlashCommand {
    companion object {
        private const val COMMAND = "review"
        private const val BUTTON_PREFIX = "member-gate-review:"
        private const val INTERRUPT_CONFIRM_PREFIX = "member-gate-review-interrupt:"
        private const val APPROVE_ACTION = "approve"
        private const val REJECT_ACTION = "reject"
        private const val MANUAL_ACTION = "manual"
        private const val INTERRUPT_CONFIRM_ACTION = "confirm"
        private const val INTERRUPT_CANCEL_ACTION = "cancel"

        private data class ReviewButtonAction(
            val action: String,
            val expectedUserId: Long,
            val expectedQueuedAt: Long
        )

        private data class InterruptButtonAction(
            val action: String,
            val reviewerId: Long,
            val targetReviewerIds: Set<Long>
        )
    }

    override fun getCommandsData(): List<SlashCommandData> {
        return listOf(
            Commands.slash(COMMAND, "Review pending member gate applications")
                .setContexts(InteractionContextType.GUILD)
                .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.MANAGE_ROLES))
        )
    }

    override fun onSlashCommandInteraction(event: SlashCommandInteractionEvent) {
        val guild = event.guild
        if (event.name != COMMAND || guild == null) {
            return
        }

        if (event.member?.hasPermission(Permission.MANAGE_ROLES) != true) {
            event.reply("You need the manage roles permission to use this command.").setEphemeral(true).queue()
            return
        }

        val storedSession = reviewSessionRegistry.get(guild.idLong, event.user.idLong)
        if (storedSession != null && continueStoredSession(event, guild, storedSession)) {
            return
        }

        val session = reviewManager.createSession(guild.idLong)
        if (session == null) {
            event.reply("Nobody is currently waiting for approval.").setEphemeral(true).queue()
            return
        }

        val pendingQuestion = resolveCurrentQuestion(guild, event.jda, session)
        if (pendingQuestion == null) {
            reviewSessionRegistry.forget(guild.idLong, event.user.idLong)
            event.reply("Nobody is currently waiting for approval.").setEphemeral(true).queue()
            return
        }

        val otherSessions = reviewSessionRegistry.getOtherSessions(guild.idLong, event.user.idLong)
        if (otherSessions.isNotEmpty()) {
            event.reply(buildInterruptPrompt(guild, otherSessions))
                .setEphemeral(true)
                .addComponents(ActionRow.of(buildInterruptButtons(event.user.idLong, otherSessions)))
                .queue()
            return
        }

        replyWithNewReviewSession(event, guild, session, pendingQuestion)
    }

    private fun replyWithNewReviewSession(
        event: SlashCommandInteractionEvent,
        guild: Guild,
        session: ReviewSession,
        pendingQuestion: MemberGateQuestion
    ) {
        reviewSessionRegistry.remember(guild.idLong, event.user.idLong, session)
        logReviewStarted(guild, event.member!!, session)

        event.reply(buildReviewMessage(guild, session, pendingQuestion))
            .setEphemeral(true)
            .addComponents(ActionRow.of(buildButtons(pendingQuestion)))
            .queue()
    }

    private fun continueStoredSession(event: SlashCommandInteractionEvent, guild: Guild, session: ReviewSession): Boolean {
        val pendingQuestion = resolveCurrentQuestion(guild, event.jda, session)
        if (pendingQuestion == null) {
            reviewSessionRegistry.forget(guild.idLong, event.user.idLong)
            return false
        }

        reviewSessionRegistry.remember(guild.idLong, event.user.idLong, session)
        event.reply(buildReviewMessage(guild, session, pendingQuestion))
            .setEphemeral(true)
            .addComponents(ActionRow.of(buildButtons(pendingQuestion)))
            .queue()
        return true
    }

    override fun onButtonInteraction(event: ButtonInteractionEvent) {
        val interruptAction = parseInterruptButtonAction(event.componentId)
        if (interruptAction != null) {
            handleInterruptButton(event, interruptAction)
            return
        }

        if (!event.componentId.startsWith(BUTTON_PREFIX)) {
            return
        }

        val guild = event.guild
        if (guild == null || event.member?.hasPermission(Permission.MANAGE_ROLES) != true) {
            event.reply("You need the manage roles permission to use this command.").setEphemeral(true).queue()
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
        if (currentQuestion == null) {
            session.advancePastResolvedCurrent()
            continueAfterRemovedCurrent(event, guild, session)
            return
        }

        if (currentQuestion.queuedAt != buttonAction.expectedQueuedAt) {
            event.reply("This review message is out of date. Run `/review` again.")
                .setEphemeral(true)
                .queue()
            return
        }

        val feedback = when (buttonAction.action) {
            APPROVE_ACTION -> {
                val result = reviewManager.approve(guild, event.jda, currentUserId)
                session.recordApproval()
                session.advanceAfterReview()
                result
            }

            REJECT_ACTION -> {
                val result = reviewManager.reject(guild, event.jda, currentUserId)
                session.recordRejection()
                session.advanceAfterReview()
                result
            }

            MANUAL_ACTION -> {
                val result = reviewManager.reject(guild, event.jda, currentUserId, manualAction = true)
                session.recordManualAction()
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

        val pendingQuestion = resolveCurrentQuestion(guild, event.jda, session)
        if (pendingQuestion == null) {
            logReviewCompleted(guild, event.member!!, session)
            reviewSessionRegistry.forget(guild.idLong, event.user.idLong)
            event.editMessage(buildCompletionMessage(feedback)).setComponents(emptyList()).queue()
            return
        }

        reviewSessionRegistry.remember(guild.idLong, event.user.idLong, session)

        event.editMessage(buildReviewMessage(guild, session, pendingQuestion, feedback))
            .setComponents(ActionRow.of(buildButtons(pendingQuestion)))
            .queue()
    }

    private fun continueAfterRemovedCurrent(event: ButtonInteractionEvent, guild: Guild, session: ReviewSession) {
        val feedback = "The applicant left; no further action is needed."
        val pendingQuestion = resolveCurrentQuestion(guild, event.jda, session)
        if (pendingQuestion == null) {
            logReviewCompleted(guild, event.member!!, session)
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

    private fun handleInterruptButton(event: ButtonInteractionEvent, buttonAction: InterruptButtonAction) {
        val guild = event.guild
        if (guild == null || event.member?.hasPermission(Permission.MANAGE_ROLES) != true) {
            event.reply("You need the manage roles permission to use this command.").setEphemeral(true).queue()
            return
        }

        if (buttonAction.reviewerId != event.user.idLong) {
            event.reply("This confirmation prompt belongs to another moderator.").setEphemeral(true).queue()
            return
        }

        if (buttonAction.action == INTERRUPT_CANCEL_ACTION) {
            event.editMessage("Review start cancelled. The other moderator's review session was not interrupted.")
                .setComponents(emptyList())
                .queue()
            return
        }

        if (buttonAction.action != INTERRUPT_CONFIRM_ACTION) {
            event.reply("This review action is no longer available. Run `/review` again.")
                .setEphemeral(true)
                .queue()
            return
        }

        val currentOtherSessions = reviewSessionRegistry.getOtherSessions(guild.idLong, event.user.idLong)
        val currentTargetReviewerIds = currentOtherSessions.map { it.reviewerId }.toSet()
        if (currentTargetReviewerIds != buttonAction.targetReviewerIds) {
            event.editMessage("The active review sessions changed. Run `/review` again to confirm the current sessions.")
                .setComponents(emptyList())
                .queue()
            return
        }

        val session = reviewManager.createSession(guild.idLong)
        if (session == null) {
            event.editMessage("Nobody is currently waiting for approval.").setComponents(emptyList()).queue()
            return
        }

        val pendingQuestion = resolveCurrentQuestion(guild, event.jda, session)
        if (pendingQuestion == null) {
            reviewSessionRegistry.forget(guild.idLong, event.user.idLong)
            event.editMessage("Nobody is currently waiting for approval.").setComponents(emptyList()).queue()
            return
        }

        reviewSessionRegistry.forgetSessions(guild.idLong, buttonAction.targetReviewerIds)
            .forEach { logReviewInterrupted(guild, it) }
        reviewSessionRegistry.remember(guild.idLong, event.user.idLong, session)
        logReviewStarted(guild, event.member!!, session)

        event.editMessage(buildReviewMessage(guild, session, pendingQuestion))
            .setComponents(ActionRow.of(buildButtons(pendingQuestion)))
            .queue()
    }

    private fun parseInterruptButtonAction(componentId: String): InterruptButtonAction? {
        if (!componentId.startsWith(INTERRUPT_CONFIRM_PREFIX)) {
            return null
        }

        val segments = componentId.removePrefix(INTERRUPT_CONFIRM_PREFIX).split(":", limit = 3)
        if (segments.size != 3) {
            return null
        }

        val reviewerId = segments[1].toLongOrNull() ?: return null
        val targetReviewerIds = segments[2]
            .split(",")
            .filter { it.isNotBlank() }
            .map { it.toLongOrNull() ?: return null }
            .toSet()
        return InterruptButtonAction(segments[0], reviewerId, targetReviewerIds)
    }

    private fun resolveCurrentQuestion(guild: Guild, jda: JDA, session: ReviewSession): MemberGateQuestion? {
        while (true) {
            val currentUserId = session.getCurrentUserId() ?: return null
            val question = reviewManager.getPendingQuestion(guild.idLong, currentUserId)
            if (question != null && guild.getMemberById(currentUserId) != null) {
                return question
            }

            if (question != null) {
                reviewManager.clearPendingQuestion(guild.idLong, jda, currentUserId)
            }

            session.advancePastResolvedCurrent()
        }
    }

    private fun buildReviewMessage(
        guild: Guild,
        session: ReviewSession,
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

    private fun buildInterruptPrompt(
        guild: Guild,
        sessions: List<ReviewSessionRegistry.StoredReviewSession>
    ): String {
        val sessionLines = sessions.joinToString("\n") { storedSession ->
            val reviewer = guild.getMemberById(storedSession.reviewerId)
            val reviewerName = reviewer?.user?.asMention ?: "<@${storedSession.reviewerId}>"
            val pendingCount = storedSession.session.toPendingUserIds().size
            "- $reviewerName has $pendingCount pending applicant(s), last updated ${formatUpdatedAt(storedSession.updatedAt)}."
        }

        return "Another moderator is already reviewing members:\n" +
                sessionLines +
                "\n\nDo you want to interrupt their session and start yours?"
    }

    private fun formatUpdatedAt(updatedAt: Instant): String {
        return if (updatedAt == Instant.EPOCH) {
            "at an unknown time"
        } else {
            TimeFormat.RELATIVE.atInstant(updatedAt).toString()
        }
    }

    private fun logReviewStarted(guild: Guild, moderator: Member, session: ReviewSession) {
        val logEmbed = EmbedBuilder()
            .setColor(Color.GREEN)
            .setTitle("Member gate review started")
            .addField("Moderator", moderator.nicknameAndUsername, false)
            .addField("Pending applicants", session.toPendingUserIds().size.toString(), true)

        guildLogger.log(logEmbed, moderator.user, guild, null, GuildLogger.LogTypeAction.MODERATOR)
    }

    private fun logReviewCompleted(guild: Guild, moderator: Member, session: ReviewSession) {
        logReviewSummary(guild, moderator.user, moderator.nicknameAndUsername, "Member gate review completed", session)
    }

    private fun logReviewInterrupted(guild: Guild, storedSession: ReviewSessionRegistry.StoredReviewSession) {
        val reviewer = guild.getMemberById(storedSession.reviewerId)
        logReviewSummary(
            guild = guild,
            moderatorUser = reviewer?.user,
            moderatorName = reviewer?.nicknameAndUsername ?: "<@${storedSession.reviewerId}>",
            title = "Member gate review interrupted",
            session = storedSession.session
        )
    }

    private fun logReviewSummary(
        guild: Guild,
        moderatorUser: User?,
        moderatorName: String,
        title: String,
        session: ReviewSession
    ) {
        val logEmbed = EmbedBuilder()
            .setColor(Color.GREEN)
            .setTitle(title)
            .addField("Moderator", moderatorName, false)
            .addField("Approved", session.approvedCount.toString(), true)
            .addField("Rejected", session.rejectedCount.toString(), true)
            .addField("Manual action", session.manualActionCount.toString(), true)

        guildLogger.log(logEmbed, moderatorUser, guild, null, GuildLogger.LogTypeAction.MODERATOR)
    }

    private fun buildButtons(question: MemberGateQuestion) = listOf(
        Button.success("$BUTTON_PREFIX$APPROVE_ACTION:${question.userId}:${question.queuedAt}", "Approve"),
        Button.danger("$BUTTON_PREFIX$REJECT_ACTION:${question.userId}:${question.queuedAt}", "Reject"),
        Button.primary("$BUTTON_PREFIX$MANUAL_ACTION:${question.userId}:${question.queuedAt}", "Manual Action")
    )

    private fun buildInterruptButtons(
        reviewerId: Long,
        sessions: List<ReviewSessionRegistry.StoredReviewSession>
    ): List<Button> {
        val targetReviewerIds = sessions.joinToString(",") { it.reviewerId.toString() }
        return listOf(
            Button.danger(
                "$INTERRUPT_CONFIRM_PREFIX$INTERRUPT_CONFIRM_ACTION:$reviewerId:$targetReviewerIds",
                "Interrupt review"
            ),
            Button.secondary(
                "$INTERRUPT_CONFIRM_PREFIX$INTERRUPT_CANCEL_ACTION:$reviewerId:$targetReviewerIds",
                "Cancel"
            )
        )
    }
}
