package be.duncanc.discordmodbot.reporting

import be.duncanc.discordmodbot.discord.SlashCommand
import be.duncanc.discordmodbot.discord.UserBlockService
import be.duncanc.discordmodbot.discord.nicknameAndUsername
import be.duncanc.discordmodbot.reporting.persistence.ReportChannelRepository
import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.components.label.Label
import net.dv8tion.jda.api.components.textinput.TextInput
import net.dv8tion.jda.api.components.textinput.TextInputStyle
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.Member
import net.dv8tion.jda.api.entities.MessageEmbed
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.hooks.ListenerAdapter
import net.dv8tion.jda.api.interactions.InteractionContextType
import net.dv8tion.jda.api.interactions.commands.build.Commands
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData
import net.dv8tion.jda.api.modals.Modal
import org.springframework.stereotype.Component
import java.awt.Color
import java.time.OffsetDateTime

@Component
class FeedbackCommand(
    private val reportChannelRepository: ReportChannelRepository,
    private val userBlockService: UserBlockService
) : ListenerAdapter(), SlashCommand {
    companion object {
        private const val COMMAND = "feedback"
        private const val DESCRIPTION = "Privately share feedback with the server staff."
        private const val MODAL_PREFIX = "feedback-modal"
        private const val FIELD_MESSAGE = "message"
        private const val MESSAGE_MAX_LENGTH = 1000
    }

    override fun onSlashCommandInteraction(event: SlashCommandInteractionEvent) {
        if (event.name != COMMAND) {
            return
        }

        val guild = event.guild
        val member = event.member
        if (guild == null || member == null) {
            event.reply("This command only works in a server.").setEphemeral(true).queue()
            return
        }

        if (userBlockService.isBlocked(event.user.idLong)) {
            event.reply("You are blocked from using this command.").setEphemeral(true).queue()
            return
        }

        val channelId = reportChannelRepository.findById(guild.idLong).orElse(null)?.textChannelId
        if (channelId == null) {
            event.reply("Feedback is not enabled on this server.").setEphemeral(true).queue()
            return
        }

        if (guild.getTextChannelById(channelId) == null) {
            event.reply("Feedback is configured to use a channel that no longer exists. Please contact server staff.")
                .setEphemeral(true)
                .queue()
            return
        }

        showModal(event, createFeedbackModal(guild.idLong, event.user.idLong))
    }

    override fun onModalInteraction(event: ModalInteractionEvent) {
        val modalState = parseModalState(event.modalId) ?: return

        val guild = event.guild
        val member = event.member
        if (guild == null || member == null) {
            event.reply("This command only works in a server.").setEphemeral(true).queue()
            return
        }

        if (modalState.guildId != guild.idLong || modalState.userId != event.user.idLong) {
            event.reply("This feedback form is out of date. Run `/feedback` again.").setEphemeral(true).queue()
            return
        }

        if (userBlockService.isBlocked(event.user.idLong)) {
            event.reply("You are blocked from using this command.").setEphemeral(true).queue()
            return
        }

        val feedbackMessage = getFeedbackMessage(event)?.trim()
        if (feedbackMessage.isNullOrBlank()) {
            event.reply("Please provide feedback before submitting.").setEphemeral(true).queue()
            return
        }

        val channelId = reportChannelRepository.findById(guild.idLong).orElse(null)?.textChannelId
        if (channelId == null) {
            event.reply("Feedback is not enabled on this server.").setEphemeral(true).queue()
            return
        }

        val embed = createFeedbackEmbed(member, feedbackMessage)
        sendFeedbackEmbed(
            guild = guild,
            channelId = channelId,
            embed = embed,
            onSuccess = {
                event.reply("Your feedback has been transferred to the moderators. Thank you for helping us.")
                    .setEphemeral(true)
                    .queue()
            },
            onMissingChannel = {
                event.reply("Feedback is configured to use a channel that no longer exists. Please contact server staff.")
                    .setEphemeral(true)
                    .queue()
            }
        )
    }

    override fun getCommandsData(): List<SlashCommandData> {
        return listOf(
            Commands.slash(COMMAND, DESCRIPTION)
                .setContexts(InteractionContextType.GUILD)
        )
    }

    internal fun showModal(event: SlashCommandInteractionEvent, modal: Modal) {
        event.replyModal(modal).queue()
    }

    internal fun getFeedbackMessage(event: ModalInteractionEvent): String? {
        return event.getValue(FIELD_MESSAGE)?.asString
    }

    internal fun sendFeedbackEmbed(
        guild: Guild,
        channelId: Long,
        embed: MessageEmbed,
        onSuccess: () -> Unit,
        onMissingChannel: () -> Unit
    ) {
        val channel = guild.getTextChannelById(channelId)
        if (channel == null) {
            onMissingChannel()
            return
        }

        channel.sendMessageEmbeds(embed).queue { onSuccess() }
    }

    internal fun createFeedbackEmbed(member: Member, message: String): MessageEmbed {
        return EmbedBuilder()
            .setAuthor(member.nicknameAndUsername, null, member.user.effectiveAvatarUrl)
            .setDescription(message)
            .setFooter(member.user.id, null)
            .setTimestamp(OffsetDateTime.now())
            .setColor(Color.GREEN)
            .build()
    }

    private fun createFeedbackModal(guildId: Long, userId: Long): Modal {
        val feedbackInput = TextInput.create(FIELD_MESSAGE, TextInputStyle.PARAGRAPH)
            .setPlaceholder("Share your feedback for the server staff...")
            .setMinLength(1)
            .setMaxLength(MESSAGE_MAX_LENGTH)
            .build()

        return Modal.create(modalId(guildId, userId), "Submit Feedback")
            .addComponents(Label.of("Feedback", "Your message will be forwarded to the server staff.", feedbackInput))
            .build()
    }

    private fun modalId(guildId: Long, userId: Long): String {
        return "$MODAL_PREFIX:$guildId:$userId"
    }

    private fun parseModalState(modalId: String): FeedbackModalState? {
        val tokens = modalId.split(':')
        if (tokens.size != 3 || tokens[0] != MODAL_PREFIX) {
            return null
        }

        val guildId = tokens[1].toLongOrNull() ?: return null
        val userId = tokens[2].toLongOrNull() ?: return null
        return FeedbackModalState(guildId, userId)
    }

    private data class FeedbackModalState(
        val guildId: Long,
        val userId: Long
    )
}
