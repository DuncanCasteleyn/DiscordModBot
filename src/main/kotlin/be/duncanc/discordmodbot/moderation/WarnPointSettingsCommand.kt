package be.duncanc.discordmodbot.moderation

import be.duncanc.discordmodbot.discord.SlashCommand
import be.duncanc.discordmodbot.moderation.persistence.GuildWarnPointsSettings
import be.duncanc.discordmodbot.moderation.persistence.GuildWarnPointsSettingsRepository
import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.hooks.ListenerAdapter
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions
import net.dv8tion.jda.api.interactions.commands.OptionType
import net.dv8tion.jda.api.interactions.commands.build.Commands
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData
import org.springframework.stereotype.Component

@Component
class WarnPointSettingsCommand(
    private val guildWarnPointsSettingsRepository: GuildWarnPointsSettingsRepository
) : ListenerAdapter(), SlashCommand {
    companion object {
        private const val COMMAND = "warnpointsettings"
        private const val DESCRIPTION = "Configure warn point settings for this server."

        private const val SUBCOMMAND_MAXPOINTS = "maxpoints"
        private const val SUBCOMMAND_SUMMARIZELIMIT = "summarizelimit"
        private const val SUBCOMMAND_TOGGLEWARNOVERRIDE = "togglewarnoverride"
        private const val SUBCOMMAND_ANNOUNCECHANNEL = "announcechannel"

        private const val OPTION_VALUE = "value"
        private const val OPTION_CHANNEL = "channel"
    }

    override fun onSlashCommandInteraction(event: SlashCommandInteractionEvent) {
        if (event.name != COMMAND) return

        val member = event.member
        if (member == null) {
            event.reply("This command only works in a guild.").setEphemeral(true).queue()
            return
        }

        if (!member.hasPermission(Permission.ADMINISTRATOR)) {
            event.reply("You need administrator permission to use this command.").setEphemeral(true).queue()
            return
        }

        val guild = event.guild!!
        val guildId = guild.idLong
        val subcommand = event.subcommandName

        if (subcommand == null) {
            showCurrentSettings(event, guildId, guild)
            return
        }

        val guildSettings = guildWarnPointsSettingsRepository.findById(guildId)
            .orElse(GuildWarnPointsSettings(guildId, announceChannelId = -1))!!

        when (subcommand) {
            SUBCOMMAND_MAXPOINTS -> {
                val value = event.getOption(OPTION_VALUE)?.asInt
                if (value == null || value < 1) {
                    event.reply("Please provide a valid value (minimum 1).").setEphemeral(true).queue()
                    return
                }
                guildSettings.maxPointsPerReason = value
                guildWarnPointsSettingsRepository.save(guildSettings)
                event.reply("Max points per reason set to $value.").setEphemeral(true).queue()
            }

            SUBCOMMAND_SUMMARIZELIMIT -> {
                val value = event.getOption(OPTION_VALUE)?.asInt
                if (value == null || value < 1) {
                    event.reply("Please provide a valid value (minimum 1).").setEphemeral(true).queue()
                    return
                }
                guildSettings.announcePointsSummaryLimit = value
                guildWarnPointsSettingsRepository.save(guildSettings)
                event.reply("Summarize limit set to $value points.").setEphemeral(true).queue()
            }

            SUBCOMMAND_TOGGLEWARNOVERRIDE -> {
                guildSettings.overrideWarnCommand = !guildSettings.overrideWarnCommand
                guildWarnPointsSettingsRepository.save(guildSettings)
                val status = if (guildSettings.overrideWarnCommand) "enabled" else "disabled"
                event.reply("Warn command override is now $status. Use /addwarnpoints instead.").setEphemeral(true)
                    .queue()
            }

            SUBCOMMAND_ANNOUNCECHANNEL -> {
                val channel = event.getOption(OPTION_CHANNEL)?.asChannel?.asTextChannel()
                if (channel == null) {
                    event.reply("Please mention a text channel.").setEphemeral(true).queue()
                    return
                }
                guildSettings.announceChannelId = channel.idLong
                guildWarnPointsSettingsRepository.save(guildSettings)
                event.reply("Announce channel set to ${channel.asMention}.").setEphemeral(true).queue()
            }

            else -> {
                showCurrentSettings(event, guildId, guild)
            }
        }
    }

    private fun showCurrentSettings(
        event: SlashCommandInteractionEvent,
        guildId: Long,
        guild: net.dv8tion.jda.api.entities.Guild
    ) {
        val guildSettings = guildWarnPointsSettingsRepository.findById(guildId)
            .orElse(GuildWarnPointsSettings(guildId, announceChannelId = -1))!!

        val announceChannel = if (guildSettings.announceChannelId > 0) {
            guild.getTextChannelById(guildSettings.announceChannelId)?.asMention
                ?: "Channel not found (ID: ${guildSettings.announceChannelId})"
        } else {
            "Not configured"
        }

        val message = buildString {
            appendLine("**Warn Point Settings for ${guild.name}**")
            appendLine()
            appendLine("• Max points per reason: `${guildSettings.maxPointsPerReason}`")
            appendLine("• Summarize limit: `${guildSettings.announcePointsSummaryLimit}`")
            appendLine("• Announce channel: $announceChannel")
            appendLine("• Warn command override: `${if (guildSettings.overrideWarnCommand) "enabled" else "disabled"}`")
        }

        event.reply(message).setEphemeral(true).queue()
    }

    override fun getCommandsData(): List<SlashCommandData> {
        return listOf(
            Commands.slash(COMMAND, DESCRIPTION)
                .addSubcommands(
                    SubcommandData(SUBCOMMAND_MAXPOINTS, "Set the maximum points allowed per reason")
                        .addOption(OptionType.INTEGER, OPTION_VALUE, "The maximum points per reason", true),
                    SubcommandData(SUBCOMMAND_SUMMARIZELIMIT, "Set the limit before a summary is announced")
                        .addOption(OptionType.INTEGER, OPTION_VALUE, "The point limit for summary", true),
                    SubcommandData(SUBCOMMAND_TOGGLEWARNOVERRIDE, "Toggle replacing /warn with /addwarnpoints"),
                    SubcommandData(SUBCOMMAND_ANNOUNCECHANNEL, "Set the channel for announce summaries")
                        .addOption(OptionType.CHANNEL, OPTION_CHANNEL, "The text channel for announcements", true)
                )
                .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.ADMINISTRATOR))
        )
    }
}
