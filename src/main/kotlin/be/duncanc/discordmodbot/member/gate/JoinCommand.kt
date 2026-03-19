package be.duncanc.discordmodbot.member.gate

import be.duncanc.discordmodbot.discord.SlashCommand
import be.duncanc.discordmodbot.logging.GuildLogger
import net.dv8tion.jda.api.components.actionrow.ActionRow
import net.dv8tion.jda.api.components.label.Label
import net.dv8tion.jda.api.components.selections.StringSelectMenu
import net.dv8tion.jda.api.components.textinput.TextInput
import net.dv8tion.jda.api.components.textinput.TextInputStyle
import net.dv8tion.jda.api.entities.Member
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent
import net.dv8tion.jda.api.hooks.ListenerAdapter
import net.dv8tion.jda.api.interactions.commands.build.Commands
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData
import net.dv8tion.jda.api.modals.Modal
import net.dv8tion.jda.api.utils.messages.MessageCreateBuilder
import org.springframework.stereotype.Component
import java.security.SecureRandom
import java.util.concurrent.ConcurrentHashMap

@Component
class JoinCommand(
    private val memberGateService: MemberGateService,
    private val reviewManager: MemberGateReviewManager,
    private val guildLogger: GuildLogger
) : ListenerAdapter(), SlashCommand {

    companion object {
        private const val COMMAND = "join"
        private const val DESCRIPTION = "Complete the entry process to gain access to the server."
        private const val SELECT_READ_ID = "join-select-read"
        private const val SELECT_ACCEPT_ID = "join-select-accept"
        private const val MODAL_ID = "join-modal"
        private const val TEXT_ANSWER_ID = "join-answer"

        private val random = SecureRandom()
    }

    private val pendingSelections = ConcurrentHashMap<JoinStateKey, JoinSelectState>()

    private data class JoinStateKey(
        val guildId: Long,
        val userId: Long
    )

    private data class JoinSelectState(
        val guildId: Long,
        val userId: Long,
        val channelId: Long,
        val messageId: Long,
        val readRules: Boolean? = null,
        val acceptRules: Boolean? = null,
        val question: String
    )

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

        val questions = memberGateService.getQuestions(guildId).toList()
        if (questions.isEmpty()) {
            accept(member)
            event.reply("Welcome! You now have access to the server.").setEphemeral(true).queue()
            return
        }

        val question = questions[random.nextInt(questions.size)]

        val readRulesMenu = StringSelectMenu.create(SELECT_READ_ID)
            .setPlaceholder("Have you read the rules?")
            .addOption("Yes", "yes")
            .addOption("No", "no")
            .build()

        val acceptRulesMenu = StringSelectMenu.create(SELECT_ACCEPT_ID)
            .setPlaceholder("Do you accept the rules?")
            .addOption("Yes", "yes")
            .addOption("No", "no")
            .build()

        val message = MessageCreateBuilder()
            .setContent("${member.asMention} Please complete the following to gain access to the server:")
            .addComponents(
                ActionRow.of(readRulesMenu),
                ActionRow.of(acceptRulesMenu)
            )
            .build()

        event.reply(message).setEphemeral(true).queue { reply ->
            val stateKey = getStateKey(guildId, memberId)
            pendingSelections[stateKey] = JoinSelectState(
                guildId = guildId,
                userId = memberId,
                channelId = event.channel.idLong,
                messageId = reply.idLong,
                question = question
            )
        }
    }

    override fun onStringSelectInteraction(event: StringSelectInteractionEvent) {
        val componentId = event.componentId

        if (componentId != SELECT_READ_ID && componentId != SELECT_ACCEPT_ID) return

        val member = event.member
        if (member == null) {
            event.reply("This command only works in a server.").setEphemeral(true).queue()
            return
        }

        val guildId = event.guild?.idLong ?: return
        val memberId = member.idLong
        val stateKey = getStateKey(guildId, memberId)
        val state = pendingSelections[stateKey]

        if (state == null) {
            event.reply("Please use the /join command first.").setEphemeral(true).queue()
            return
        }

        val selectedValue = event.selectedOptions.firstOrNull()?.value
        val isYes = selectedValue == "yes"

        val updatedState = when (componentId) {
            SELECT_READ_ID -> state.copy(readRules = isYes)
            SELECT_ACCEPT_ID -> state.copy(acceptRules = isYes)
            else -> return
        }

        if (componentId == SELECT_READ_ID) {
            if (!isYes) {
                pendingSelections.remove(stateKey)
                event.reply("${member.asMention} Please read the rules before completing the entry process.")
                    .setEphemeral(true).queue()
                return
            }
            pendingSelections[stateKey] = updatedState
            event.deferEdit().queue()
            return
        }

        if (!isYes) {
            pendingSelections.remove(stateKey)
            kickForNotAcceptingRules(event, member)
            return
        }
        pendingSelections[stateKey] = updatedState

        if (updatedState.readRules == true && updatedState.acceptRules == true) {
            val modal = createAnswerModal(updatedState.question)
            event.replyModal(modal).queue()
        } else {
            event.deferEdit().queue()
        }
    }

    override fun onModalInteraction(event: ModalInteractionEvent) {
        if (!event.modalId.startsWith(MODAL_ID)) return

        val member = event.member
        if (member == null) {
            event.reply("This command only works in a server.").setEphemeral(true).queue()
            return
        }

        val guildId = event.guild?.idLong ?: return
        val memberId = member.idLong
        val stateKey = getStateKey(guildId, memberId)
        val state = pendingSelections.remove(stateKey)

        if (state == null) {
            event.reply("Please use the /join command first.").setEphemeral(true).queue()
            return
        }

        val answer = event.getValue(TEXT_ANSWER_ID)?.asString ?: ""
        informMember(member, state.question, answer, event)

        event.reply("Your answer has been submitted. A moderator will review it shortly.")
            .setEphemeral(true).queue()
    }

    private fun createAnswerModal(question: String): Modal {
        val textInput = TextInput.create(TEXT_ANSWER_ID, TextInputStyle.PARAGRAPH)
            .setPlaceholder("Enter your answer...")
            .setMinLength(1)
            .setMaxLength(1000)
            .build()

        return Modal.create(MODAL_ID, "Complete Entry Process")
            .addComponents(Label.of(question, textInput))
            .build()
    }

    private fun accept(member: Member) {
        val guild = member.guild
        memberGateService.getMemberRole(guild.idLong, member.jda)
            ?.let { guild.addRoleToMember(member, it).queue() }
    }

    private fun kickForNotAcceptingRules(event: StringSelectInteractionEvent, member: Member) {
        val reason = "Doesn't agree with the rules."
        val guild = event.guild ?: return
        guild.kick(member)
            .reason(reason)
            .queue()
        guildLogger.logKick(
            member.user,
            guild,
            guild.selfMember,
            reason
        )
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

    private fun getStateKey(guildId: Long, userId: Long): JoinStateKey = JoinStateKey(guildId, userId)

    override fun getCommandsData(): List<SlashCommandData> {
        return listOf(Commands.slash(COMMAND, DESCRIPTION))
    }
}
