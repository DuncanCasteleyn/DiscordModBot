package be.duncanc.discordmodbot.reporting

import be.duncanc.discordmodbot.discord.SlashCommand
import be.duncanc.discordmodbot.reporting.persistence.ReportChannel
import be.duncanc.discordmodbot.reporting.persistence.ReportChannelRepository
import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.channel.ChannelType
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.hooks.ListenerAdapter
import net.dv8tion.jda.api.interactions.InteractionContextType
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions
import net.dv8tion.jda.api.interactions.commands.OptionType
import net.dv8tion.jda.api.interactions.commands.build.Commands
import net.dv8tion.jda.api.interactions.commands.build.OptionData
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData
import org.springframework.stereotype.Component

@Component
class FeedbackConfigCommand(
    private val reportChannelRepository: ReportChannelRepository
) : ListenerAdapter(), SlashCommand {
    companion object {
        private const val COMMAND = "feedbackconfig"
        private const val DESCRIPTION = "Configure the feedback destination for this server."
        private const val OPTION_CHANNEL = "channel"
        private const val SUBCOMMAND_SHOW = "show"
        private const val SUBCOMMAND_SET_CHANNEL = "set-channel"
        private const val SUBCOMMAND_DISABLE = "disable"
    }

    override fun onSlashCommandInteraction(event: SlashCommandInteractionEvent) {
        if (event.name != COMMAND) {
            return
        }

        val guild = event.guild
        val member = event.member
        if (guild == null || member == null) {
            event.reply("This command only works in a guild.").setEphemeral(true).queue()
            return
        }

        if (!member.hasPermission(Permission.MANAGE_CHANNEL)) {
            event.reply("You need manage channel permission to use this command.").setEphemeral(true).queue()
            return
        }

        when (event.subcommandName) {
            null, SUBCOMMAND_SHOW -> showCurrentSettings(event, guild)
            SUBCOMMAND_SET_CHANNEL -> {
                val channel = getRequiredTextChannel(event) ?: return
                reportChannelRepository.save(ReportChannel(guild.idLong, channel.idLong))
                event.reply("Feedback channel set to ${channel.asMention}.").setEphemeral(true).queue()
            }

            SUBCOMMAND_DISABLE -> {
                reportChannelRepository.deleteById(guild.idLong)
                event.reply("Feedback disabled.").setEphemeral(true).queue()
            }

            else -> event.reply("Please choose a valid /feedbackconfig subcommand.").setEphemeral(true).queue()
        }
    }

    override fun getCommandsData(): List<SlashCommandData> {
        return listOf(
            Commands.slash(COMMAND, DESCRIPTION)
                .setContexts(InteractionContextType.GUILD)
                .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.MANAGE_CHANNEL))
                .addSubcommands(
                    SubcommandData(SUBCOMMAND_SHOW, "Show the current feedback destination"),
                    SubcommandData(SUBCOMMAND_SET_CHANNEL, "Set the channel that receives feedback")
                        .addOptions(textChannelOption("The channel used for feedback")),
                    SubcommandData(SUBCOMMAND_DISABLE, "Disable feedback for this server")
                )
        )
    }

    internal fun getRequiredTextChannel(event: SlashCommandInteractionEvent): TextChannel? {
        val channel = event.getOption(OPTION_CHANNEL)?.asChannel?.asTextChannel()
        if (channel == null) {
            event.reply("Please choose a text channel.").setEphemeral(true).queue()
            return null
        }

        return channel
    }

    private fun showCurrentSettings(event: SlashCommandInteractionEvent, guild: Guild) {
        val configuredChannelId = reportChannelRepository.findById(guild.idLong).orElse(null)?.textChannelId
        val message = buildString {
            appendLine("Feedback settings for ${guild.name}")
            appendLine()
            appendLine("- Feedback channel: ${formatChannel(guild, configuredChannelId)}")
        }

        event.reply(message).setEphemeral(true).queue()
    }

    private fun formatChannel(guild: Guild, channelId: Long?): String {
        if (channelId == null) {
            return "Disabled"
        }

        return guild.getTextChannelById(channelId)?.asMention ?: "Channel not found (ID: $channelId)"
    }

    private fun textChannelOption(description: String): OptionData {
        return OptionData(OptionType.CHANNEL, OPTION_CHANNEL, description, true)
            .setChannelTypes(ChannelType.TEXT)
    }
}
