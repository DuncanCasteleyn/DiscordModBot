package be.duncanc.discordmodbot.moderation

import be.duncanc.discordmodbot.discord.DiscordCommand
import be.duncanc.discordmodbot.discord.nicknameAndUsername
import be.duncanc.discordmodbot.logging.GuildLogger
import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.components.actionrow.ActionRow
import net.dv8tion.jda.api.components.buttons.Button
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.Member
import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel
import net.dv8tion.jda.api.interactions.InteractionHook
import net.dv8tion.jda.api.events.interaction.command.MessageContextInteractionEvent
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent
import net.dv8tion.jda.api.hooks.ListenerAdapter
import net.dv8tion.jda.api.interactions.InteractionContextType
import net.dv8tion.jda.api.interactions.commands.build.CommandData
import net.dv8tion.jda.api.interactions.commands.build.Commands
import org.springframework.stereotype.Component
import java.awt.Color

@Component
class ReportMessageContextMenu(
    private val guildLogger: GuildLogger,
    private val muteService: MuteService,
    private val reportSettingsService: ReportSettingsService,
    private val reportRateLimitService: ReportRateLimitService,
    private val reportedMessageService: ReportedMessageService
) : ListenerAdapter(), DiscordCommand {
    companion object {
        private const val NON_URGENT_COMMAND = "Report Message"
        private const val URGENT_COMMAND = "Urgent Report Message"
        private const val MAX_FIELD_LENGTH = 1024
        private const val BUTTON_CONFIRM_PREFIX = "report:urgent:"
        private const val BUTTON_CANCEL_PREFIX = "report:cancel:"
        private const val ALREADY_REPORTED_MESSAGE = "This issue was already reported."
        private const val HERE_MENTION = "@here"
        private const val NO_MOD_LOG_CHANNEL_MESSAGE =
            "Message reporting is not configured on this server. The bot needs a moderator log channel with permission to send messages and embeds."
    }

    private data class UrgentConfirmationAction(
        val confirm: Boolean,
        val guildId: Long,
        val channelId: Long,
        val messageId: Long,
        val reporterId: Long
    )

    override fun onMessageContextInteraction(event: MessageContextInteractionEvent) {
        val urgent = when (event.name) {
            NON_URGENT_COMMAND -> false
            URGENT_COMMAND -> true
            else -> return
        }

        val guild = event.guild
        val reporter = event.member
        if (guild == null || reporter == null) {
            event.reply("This command only works in a guild.").setEphemeral(true).queue()
            return
        }

        if (!reportSettingsService.isReportingEnabled(guild.idLong)) {
            event.reply("Message reporting is not enabled on this server.").setEphemeral(true).queue()
            return
        }

        if (reporter.isTimedOut || muteService.isUserMuted(guild.idLong, reporter.idLong)) {
            event.reply("You cannot report messages while timed out or muted.").setEphemeral(true).queue()
            return
        }

        if (reportSettingsService.isUserBlocked(guild.idLong, reporter.idLong)) {
            event.reply("You are not allowed to report messages in this server.").setEphemeral(true).queue()
            return
        }

        val target = event.target
        if (reporter.idLong == target.author.idLong) {
            event.reply("You cannot report your own messages.").setEphemeral(true).queue()
            return
        }

        val existingState = reportedMessageService.getState(guild.idLong, target.guildChannel.idLong, target.idLong)
        if (
            existingState == ReportedMessageState.URGENT ||
            (existingState == ReportedMessageState.NON_URGENT && !urgent)
        ) {
            event.reply(ALREADY_REPORTED_MESSAGE).setEphemeral(true).queue()
            return
        }

        if (existingState == ReportedMessageState.NON_URGENT) {
            if (reportRateLimitService.hasActiveToken(guild.idLong, reporter.idLong)) {
                event.reply("You can only report one message every ${reportRateLimitService.rateLimitDescription()}.")
                    .setEphemeral(true)
                    .queue()
                return
            }
            if (!guildLogger.canSendModeratorLog(guild)) {
                event.reply(NO_MOD_LOG_CHANNEL_MESSAGE).setEphemeral(true).queue()
                return
            }
            event.reply("This message was already reported as non-urgent. Confirm if you want to report it as urgent.")
                .setEphemeral(true)
                .addComponents(ActionRow.of(buildUrgentConfirmationButtons(guild.idLong, target, reporter.idLong)))
                .queue()
            return
        }

        if (reportRateLimitService.hasActiveToken(guild.idLong, reporter.idLong)) {
            event.reply("You can only report one message every ${reportRateLimitService.rateLimitDescription()}.")
                .setEphemeral(true)
                .queue()
            return
        }

        if (!guildLogger.canSendModeratorLog(guild)) {
            event.reply(NO_MOD_LOG_CHANNEL_MESSAGE).setEphemeral(true).queue()
            return
        }

        logReport(guild, reporter, target, urgent)

        if (!tryConsumeRateLimit(event, guild, reporter)) {
            return
        }

        reportedMessageService.markReported(guild.idLong, target.guildChannel.idLong, target.idLong, urgent)
        event.reply("Your report has been sent to the moderation team.").setEphemeral(true).queue()
    }

    override fun onButtonInteraction(event: ButtonInteractionEvent) {
        val action = parseUrgentConfirmationAction(event.componentId) ?: return

        if (event.user.idLong != action.reporterId) {
            event.reply("This confirmation is only for the user who requested it.").setEphemeral(true).queue()
            return
        }

        if (!action.confirm) {
            event.editMessage("Urgent report cancelled.").setComponents(emptyList()).queue()
            return
        }

        val guild = event.guild
        val reporter = event.member
        if (guild == null || reporter == null || guild.idLong != action.guildId) {
            event.reply("This report confirmation is no longer valid.").setEphemeral(true).queue()
            return
        }

        if (!reportSettingsService.isReportingEnabled(guild.idLong)) {
            event.editMessage("Message reporting is not enabled on this server.").setComponents(emptyList()).queue()
            return
        }

        if (reporter.isTimedOut || muteService.isUserMuted(guild.idLong, reporter.idLong)) {
            event.editMessage("You cannot report messages while timed out or muted.")
                .setComponents(emptyList())
                .queue()
            return
        }

        if (reportSettingsService.isUserBlocked(guild.idLong, reporter.idLong)) {
            event.editMessage("You are not allowed to report messages in this server.")
                .setComponents(emptyList())
                .queue()
            return
        }

        val existingState = reportedMessageService.getState(guild.idLong, action.channelId, action.messageId)
        if (existingState == ReportedMessageState.URGENT) {
            event.editMessage(ALREADY_REPORTED_MESSAGE).setComponents(emptyList()).queue()
            return
        }

        if (existingState != ReportedMessageState.NON_URGENT) {
            event.editMessage("This report confirmation expired. Report the message again.")
                .setComponents(emptyList())
                .queue()
            return
        }

        if (reportRateLimitService.hasActiveToken(guild.idLong, reporter.idLong)) {
            event.editMessage("You can only report one message every ${reportRateLimitService.rateLimitDescription()}.")
                .setComponents(emptyList())
                .queue()
            return
        }

        if (!guildLogger.canSendModeratorLog(guild)) {
            event.editMessage(NO_MOD_LOG_CHANNEL_MESSAGE).setComponents(emptyList()).queue()
            return
        }

        val channel = event.jda.getChannelById(MessageChannel::class.java, action.channelId)
        if (channel == null) {
            event.editMessage("I could not find that message anymore.").setComponents(emptyList()).queue()
            return
        }

        event.deferEdit().queue { hook ->
            channel.retrieveMessageById(action.messageId).queue(
                { message ->
                    logReport(guild, reporter, message, urgent = true)
                    if (!tryConsumeRateLimit(hook, guild, reporter)) {
                        return@queue
                    }
                    reportedMessageService.markUrgent(guild.idLong, action.channelId, action.messageId)
                    hook.editOriginal("Your urgent report has been sent to the moderation team.")
                        .setComponents(emptyList())
                        .queue()
                },
                {
                    hook.editOriginal("I could not find that message anymore.").setComponents(emptyList()).queue()
                }
            )
        }
    }

    override fun getCommandsData(): List<CommandData> {
        return listOf(
            Commands.message(NON_URGENT_COMMAND)
                .setContexts(InteractionContextType.GUILD),
            Commands.message(URGENT_COMMAND)
                .setContexts(InteractionContextType.GUILD)
        )
    }

    private fun logReport(guild: Guild, reporter: Member, target: Message, urgent: Boolean) {
        val ping = if (urgent) reportSettingsService.getUrgentMention(guild) else HERE_MENTION
        guildLogger.logWithContent(
            logEmbed = createReportEmbed(target, reporter, urgent),
            associatedUser = target.author,
            guild = guild,
            actionType = GuildLogger.LogTypeAction.MODERATOR,
            content = ping
        )
    }

    private fun tryConsumeRateLimit(event: MessageContextInteractionEvent, guild: Guild, reporter: Member): Boolean {
        if (reportRateLimitService.tryConsume(guild.idLong, reporter.idLong)) {
            return true
        }

        event.reply("You can only report one message every ${reportRateLimitService.rateLimitDescription()}.")
            .setEphemeral(true)
            .queue()
        return false
    }

    private fun tryConsumeRateLimit(event: ButtonInteractionEvent, guild: Guild, reporter: Member): Boolean {
        if (reportRateLimitService.tryConsume(guild.idLong, reporter.idLong)) {
            return true
        }

        event.reply("You can only report one message every ${reportRateLimitService.rateLimitDescription()}.")
            .setEphemeral(true)
            .queue()
        return false
    }

    private fun tryConsumeRateLimit(hook: InteractionHook, guild: Guild, reporter: Member): Boolean {
        if (reportRateLimitService.tryConsume(guild.idLong, reporter.idLong)) {
            return true
        }

        hook.editOriginal("You can only report one message every ${reportRateLimitService.rateLimitDescription()}.")
            .setComponents(emptyList())
            .queue()
        return false
    }

    private fun buildUrgentConfirmationButtons(guildId: Long, target: Message, reporterId: Long): List<Button> {
        val state = "$guildId:${target.guildChannel.idLong}:${target.idLong}:$reporterId"
        return listOf(
            Button.danger("$BUTTON_CONFIRM_PREFIX$state", "Report as urgent"),
            Button.secondary("$BUTTON_CANCEL_PREFIX$state", "Cancel")
        )
    }

    private fun parseUrgentConfirmationAction(componentId: String): UrgentConfirmationAction? {
        val confirm = when {
            componentId.startsWith(BUTTON_CONFIRM_PREFIX) -> true
            componentId.startsWith(BUTTON_CANCEL_PREFIX) -> false
            else -> return null
        }
        val prefix = if (confirm) BUTTON_CONFIRM_PREFIX else BUTTON_CANCEL_PREFIX
        val tokens = componentId.removePrefix(prefix).split(':')
        if (tokens.size != 4) {
            return null
        }

        return UrgentConfirmationAction(
            confirm = confirm,
            guildId = tokens[0].toLongOrNull() ?: return null,
            channelId = tokens[1].toLongOrNull() ?: return null,
            messageId = tokens[2].toLongOrNull() ?: return null,
            reporterId = tokens[3].toLongOrNull() ?: return null
        )
    }

    private fun createReportEmbed(target: Message, reporter: Member, urgent: Boolean): EmbedBuilder {
        val authorName = target.member?.nicknameAndUsername ?: target.author.name
        val embed = EmbedBuilder()
            .setColor(if (urgent) Color.RED else Color.YELLOW)
            .setTitle(if (urgent) "Urgent message report" else "Message report")
            .addField("Reporter", "${reporter.nicknameAndUsername} (${reporter.id})", true)
            .addField("Reported user", authorName, true)
            .addField("Channel", target.guildChannel.asMention, true)
            .addField("Message URL", "[Link](${target.jumpUrl})", false)

        if (target.contentRaw.isNotBlank()) {
            embed.addField("Message", target.contentRaw.abbreviateField(), false)
        }

        val attachments = target.attachments.joinToString("\n") { it.url }
        if (attachments.isNotBlank()) {
            embed.addField("Attachment(s)", attachments.abbreviateField(), false)
        }

        return embed
    }

    private fun String.abbreviateField(): String {
        if (length <= MAX_FIELD_LENGTH) {
            return this
        }

        return take(MAX_FIELD_LENGTH - 3) + "..."
    }
}
