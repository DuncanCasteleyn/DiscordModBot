package be.duncanc.discordmodbot.moderation

import be.duncanc.discordmodbot.discord.SlashCommand
import be.duncanc.discordmodbot.moderation.AudioFilterService.Companion.MAX_TIMEOUT_MINUTES
import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.entities.Guild
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
class AudioFilterConfigCommand(
    private val audioFilterService: AudioFilterService
) : ListenerAdapter(), SlashCommand {
    companion object {
        private const val COMMAND = "audiofilter"
        private const val DESCRIPTION = "Configure the audio file filter."

        private const val SUBCOMMAND_SHOW = "show"
        private const val SUBCOMMAND_ENABLE = "enable"
        private const val SUBCOMMAND_DISABLE = "disable"
        private const val SUBCOMMAND_TIMEOUT = "timeout"

        private const val OPTION_TIMEOUT = "timeout"
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

        if (!member.hasPermission(Permission.ADMINISTRATOR)) {
            event.reply("You need administrator permission to use this command.").setEphemeral(true).queue()
            return
        }

        when (event.subcommandName) {
            SUBCOMMAND_SHOW -> showCurrentSettings(event, guild)
            SUBCOMMAND_ENABLE -> enableFilter(event, guild)
            SUBCOMMAND_DISABLE -> {
                audioFilterService.disableFilter(guild.idLong)
                event.reply("Audio file filter disabled.").setEphemeral(true).queue()
            }

            SUBCOMMAND_TIMEOUT -> setTimeout(event, guild)
            else -> event.reply("Please choose a valid /audiofilter subcommand.").setEphemeral(true).queue()
        }
    }

    override fun getCommandsData(): List<SlashCommandData> {
        return listOf(
            Commands.slash(COMMAND, DESCRIPTION)
                .addSubcommands(
                    SubcommandData(SUBCOMMAND_SHOW, "Show the current audio file filter settings"),
                    SubcommandData(SUBCOMMAND_ENABLE, "Enable the audio file filter for this server")
                        .addOptions(timeoutOption("Optional timeout in minutes applied when a member posts audio", false)),
                    SubcommandData(SUBCOMMAND_DISABLE, "Disable the audio file filter for this server"),
                    SubcommandData(SUBCOMMAND_TIMEOUT, "Change or clear the timeout for posting audio")
                        .addOptions(timeoutOption("Timeout in minutes, omit to remove the timeout", true))
                )
                .setContexts(InteractionContextType.GUILD)
                .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.ADMINISTRATOR))
        )
    }

    private fun enableFilter(event: SlashCommandInteractionEvent, guild: Guild) {
        val timeoutMinutes = event.getOption(OPTION_TIMEOUT)?.asLong
        audioFilterService.enableFilter(guild.idLong, timeoutMinutes)

        val message = if (timeoutMinutes == null) {
            "Audio file filter enabled. Posted audio files will be deleted."
        } else {
            "Audio file filter enabled. Posted audio files will be deleted and the poster will be timed out for $timeoutMinutes minutes."
        }
        event.reply(message).setEphemeral(true).queue()
    }

    private fun setTimeout(event: SlashCommandInteractionEvent, guild: Guild) {
        val timeoutMinutes = event.getOption(OPTION_TIMEOUT)?.asLong
        val updatedSettings = audioFilterService.setTimeout(guild.idLong, timeoutMinutes)
        if (updatedSettings == null) {
            event.reply("The audio file filter is not enabled. Use /audiofilter enable first.").setEphemeral(true).queue()
            return
        }

        val message = if (timeoutMinutes == null) {
            "Timeout removed. Posted audio files will only be deleted."
        } else {
            "Members posting audio files will now be timed out for $timeoutMinutes minutes."
        }
        event.reply(message).setEphemeral(true).queue()
    }

    private fun showCurrentSettings(event: SlashCommandInteractionEvent, guild: Guild) {
        val settings = audioFilterService.getSettings(guild.idLong)

        val message = buildString {
            appendLine("Audio file filter settings for ${guild.name}")
            appendLine()
            appendLine("- Filter: ${if (settings == null) "Disabled" else "Enabled"}")
            appendLine("- Action: Auto delete posted audio files")
            appendLine(
                "- Timeout: " + (settings?.timeoutMinutes?.let { "$it minutes" } ?: "None")
            )
        }

        event.reply(message).setEphemeral(true).queue()
    }

    private fun timeoutOption(description: String, required: Boolean): OptionData {
        return OptionData(OptionType.INTEGER, OPTION_TIMEOUT, description, required)
            .setMinValue(1)
            .setMaxValue(MAX_TIMEOUT_MINUTES)
    }
}
