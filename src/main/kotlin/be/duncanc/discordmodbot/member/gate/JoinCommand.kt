package be.duncanc.discordmodbot.member.gate

import be.duncanc.discordmodbot.discord.SlashCommand
import be.duncanc.discordmodbot.logging.GuildLogger
import net.dv8tion.jda.api.components.label.Label
import net.dv8tion.jda.api.components.selections.SelectOption
import net.dv8tion.jda.api.components.selections.StringSelectMenu
import net.dv8tion.jda.api.components.textinput.TextInput
import net.dv8tion.jda.api.components.textinput.TextInputStyle
import net.dv8tion.jda.api.entities.Member
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel
import net.dv8tion.jda.api.entities.emoji.Emoji
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.hooks.ListenerAdapter
import net.dv8tion.jda.api.interactions.commands.build.Commands
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData
import net.dv8tion.jda.api.modals.Modal
import org.springframework.stereotype.Component
import java.security.SecureRandom


private const val QUESTION_ID = "question"
private const val COMMAND = "join"
private const val DESCRIPTION = "Complete the entry process to gain access to the server."
private const val MODAL_ID = "join-modal"
private const val RULES_ANSWER_ID = "rules-answer"
private const val TEXT_ANSWER_ID = "join-answer"

@Component
class JoinCommand(
    private val memberGateService: MemberGateService,
    private val reviewManager: ReviewManager,
    private val guildLogger: GuildLogger
) : ListenerAdapter(), SlashCommand {

    companion object {
        private val random = SecureRandom()
    }

    override fun onSlashCommandInteraction(event: SlashCommandInteractionEvent) {
        if (event.name != COMMAND) return

        val member = event.member
        if (member == null) {
            event.reply("This command only works in a server.").setEphemeral(true).queue()
            return
        }

        val guild = event.guild ?: return
        val guildId = guild.idLong
        val memberId = member.idLong

        val memberRole = memberGateService.getMemberRole(guildId, event.jda)
        if (memberRole == null || member.roles.any { it.idLong == memberRole.idLong }) {
            event.reply("You already have access to the server.").setEphemeral(true).queue()
            return
        }

        if (reviewManager.hasPendingQuestion(guildId, memberId)) {
            event.reply("You have already tried answering a question. A moderator now needs to manually review you. Please be patient.")
                .setEphemeral(true).queue()
            return
        }

        val modalQuestion = memberGateService.getModalQuestion(guildId, memberId)

        val question = if (modalQuestion != null) {
            modalQuestion
        } else {
            val questions = memberGateService.getQuestions(guildId).toList()
            if (questions.isEmpty()) {
                null
            } else {
                val selected = questions[random.nextInt(questions.size)]
                memberGateService.saveModalQuestion(guildId, memberId, selected)
                selected
            }
        }

        if (question == null) {
            accept(member)
            event.reply("Welcome! You now have access to the server.").setEphemeral(true).queue()
            return
        }

        val modal = createAnswerModal(question)
        event.replyModal(modal).queue()
    }

    override fun onModalInteraction(event: ModalInteractionEvent) {
        if (!event.modalId.startsWith(MODAL_ID)) return

        val member = event.member
        if (member == null) {
            event.reply("This modal only works in a server.").setEphemeral(true).queue()
            return
        }

        if (event.getValue(RULES_ANSWER_ID)?.asStringList?.contains("kick-me") == true) {
            executeKickForDisagreement(event, member)

            return
        }

        val guildId = member.guild.idLong

        val answer = event.getValue(TEXT_ANSWER_ID)?.asString ?: ""
        val question =
            event.getValue(QUESTION_ID)?.asStringList?.getOrNull(0) ?: memberGateService.getModalQuestion(
                guildId,
                member.idLong
            )

        if (question == null) {
            event.reply("You took too long to answer the question. Please try again.").setEphemeral(true).queue()
            return
        }

        memberGateService.deleteModalQuestion(guildId, member.idLong)

        informMember(member, question, answer, event)

        event.reply("Your answer has been submitted. A moderator will review it shortly.")
            .setEphemeral(true).queue()
    }

    private fun executeKickForDisagreement(
        event: ModalInteractionEvent,
        member: Member
    ) {
        val reason = "Doesn't agree with the rules."

        val guild = event.guild ?: return

        member.kick()
            .reason(reason)
            .queue()

        guildLogger.logKick(
            member.user,
            guild,
            guild.selfMember,
            reason
        )
    }

    private fun createAnswerModal(question: String): Modal {
        val rulesMenu = StringSelectMenu.create(RULES_ANSWER_ID)
            .setPlaceholder("Have you read the rules and agree to them?")
            .addOption("Yes", "agree-rules", "You agree to the rules", Emoji.fromUnicode("✅"))
            .addOption("No", "kick-me", "Disagree and get removed from the server", Emoji.fromUnicode("❌"))
            .build()

        val questionOption = SelectOption.of(question, question)

        val questionMenu = StringSelectMenu.create(QUESTION_ID)
            .setPlaceholder("If you don't see a question, click on this menu and select the question to continue")
            .addOptions(questionOption)
            .setDefaultOptions(questionOption)
            .build()

        val textInput = TextInput.create(TEXT_ANSWER_ID, TextInputStyle.PARAGRAPH)
            .setPlaceholder("Enter your answer within 10 minutes...")
            .setMinLength(1)
            .setMaxLength(1000)
            .build()

        return Modal.create(MODAL_ID, "Complete Entry Process")
            .addComponents(
                Label.of(
                    "Rules",
                    "You can close this modal and execute /join again to get the same question (within 10 minutes)",
                    rulesMenu
                ),
                Label.of("Question", question, questionMenu),
                Label.of("Please answer the question above", textInput)
            )
            .build()
    }

    private fun accept(member: Member) {
        val guild = member.guild
        memberGateService.getMemberRole(guild.idLong, member.jda)
            ?.let { guild.addRoleToMember(member, it).queue() }
    }

    private fun informMember(member: Member, question: String, answer: String, event: ModalInteractionEvent) {
        val channel = event.channel
        if (channel is TextChannel) {
            channel.sendMessage(
                member.asMention + " Please wait while a moderator manually checks your answer. You might be asked (an) other question(s).\n\n" +
                        "A moderator can use `/review` to start reviewing the queue."
            ).queue { message ->
                reviewManager.rememberInformPrompt(member.guild.idLong, member.user.idLong, message.idLong)
            }
        }
        reviewManager.savePendingQuestion(member, question, answer)
    }

    override fun getCommandsData(): List<SlashCommandData> {
        return listOf(Commands.slash(COMMAND, DESCRIPTION))
    }
}
