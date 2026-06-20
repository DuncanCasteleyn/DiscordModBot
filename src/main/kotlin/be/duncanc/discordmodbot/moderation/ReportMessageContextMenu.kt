package be.duncanc.discordmodbot.moderation

import be.duncanc.discordmodbot.discord.DiscordCommand
import be.duncanc.discordmodbot.discord.nicknameAndUsername
import be.duncanc.discordmodbot.logging.GuildLogger
import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.events.interaction.command.MessageContextInteractionEvent
import net.dv8tion.jda.api.hooks.ListenerAdapter
import net.dv8tion.jda.api.interactions.InteractionContextType
import net.dv8tion.jda.api.interactions.commands.build.CommandData
import net.dv8tion.jda.api.interactions.commands.build.Commands
import org.springframework.stereotype.Component
import java.awt.Color

@Component
class ReportMessageContextMenu(
    private val guildLogger: GuildLogger
) : ListenerAdapter(), DiscordCommand {
    companion object {
        private const val NON_URGENT_COMMAND = "Report Message"
        private const val URGENT_COMMAND = "Urgent Report Message"
        private const val MAX_FIELD_LENGTH = 1024
    }

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

        val target = event.target
        val ping = if (urgent) "@everyone" else "@here"
        guildLogger.log(
            logEmbed = createReportEmbed(target, reporter.nicknameAndUsername, urgent),
            associatedUser = target.author,
            guild = guild,
            actionType = GuildLogger.LogTypeAction.MODERATOR,
            content = ping
        )

        event.reply("Your report has been sent to the moderation team.").setEphemeral(true).queue()
    }

    override fun getCommandsData(): List<CommandData> {
        return listOf(
            Commands.message(NON_URGENT_COMMAND)
                .setContexts(InteractionContextType.GUILD),
            Commands.message(URGENT_COMMAND)
                .setContexts(InteractionContextType.GUILD)
        )
    }

    private fun createReportEmbed(target: Message, reporterName: String, urgent: Boolean): EmbedBuilder {
        val authorName = target.member?.nicknameAndUsername ?: target.author.name
        val embed = EmbedBuilder()
            .setColor(if (urgent) Color.RED else Color.YELLOW)
            .setTitle(if (urgent) "Urgent message report" else "Message report")
            .addField("Reporter", reporterName, true)
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
