package be.duncanc.discordmodbot.narou.novel.api

import be.duncanc.discordmodbot.discord.SlashCommand
import be.duncanc.discordmodbot.narou.novel.api.persistence.NarouNovelAlertSettings
import be.duncanc.discordmodbot.narou.novel.api.persistence.NarouNovelAlertSettingsRepository
import be.duncanc.discordmodbot.narou.novel.api.persistence.NarouNovelPendingAlertRepository
import be.duncanc.discordmodbot.narou.novel.api.persistence.NarouNovelSnapshotRepository
import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.channel.ChannelType
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel
import net.dv8tion.jda.api.events.guild.GuildLeaveEvent
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
class NarouNovelConfigCommand(
    private val narouNovelAlertSettingsRepository: NarouNovelAlertSettingsRepository,
    private val narouNovelPendingAlertRepository: NarouNovelPendingAlertRepository,
    private val narouNovelSnapshotRepository: NarouNovelSnapshotRepository
) : ListenerAdapter(), SlashCommand {
    companion object {
        private const val COMMAND = "narounovelapi"
        private const val DESCRIPTION = "Configure Narou novel update alerts for this server."
        private const val OPTION_CHANNEL = "channel"
        private const val OPTION_THRESHOLD = "threshold"
        private const val SUBCOMMAND_SHOW = "show"
        private const val SUBCOMMAND_SET_CHANNEL = "set-channel"
        private const val SUBCOMMAND_SET_THRESHOLD = "set-threshold"
        private const val SUBCOMMAND_DISABLE = "disable"
        internal const val MINIMUM_THRESHOLD = 50L
    }

    override fun onGuildLeave(event: GuildLeaveEvent) {
        clearAlertConfiguration(event.guild.idLong)
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
                val settings = narouNovelAlertSettingsRepository.findById(guild.idLong).orElseGet {
                    NarouNovelAlertSettings(guildId = guild.idLong, channelId = channel.idLong)
                }
                settings.channelId = channel.idLong
                initializeBaselines(settings)
                narouNovelAlertSettingsRepository.save(settings)
                event.reply("Narou novel alerts will be sent to ${channel.asMention}.").setEphemeral(true).queue()
            }

            SUBCOMMAND_SET_THRESHOLD -> {
                val threshold = getRequiredThreshold(event) ?: return
                if (threshold < MINIMUM_THRESHOLD) {
                    event.reply("The length threshold must be at least 50.").setEphemeral(true).queue()
                    return
                }
                val settings = narouNovelAlertSettingsRepository.findById(guild.idLong).orElseGet {
                    NarouNovelAlertSettings(guildId = guild.idLong)
                }
                settings.lengthThreshold = threshold
                initializeBaselines(settings)
                narouNovelAlertSettingsRepository.save(settings)
                event.reply("Narou novel length alerts now require at least $threshold characters.")
                    .setEphemeral(true)
                    .queue()
            }

            SUBCOMMAND_DISABLE -> {
                clearAlertConfiguration(guild.idLong)
                event.reply("Narou novel alerts disabled.").setEphemeral(true).queue()
            }

            else -> event.reply("Please choose a valid /narounovelapi subcommand.").setEphemeral(true).queue()
        }
    }

    override fun getCommandsData(): List<SlashCommandData> {
        return listOf(
            Commands.slash(COMMAND, DESCRIPTION)
                .setContexts(InteractionContextType.GUILD)
                .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.MANAGE_CHANNEL))
                .addSubcommands(
                    SubcommandData(SUBCOMMAND_SHOW, "Show the current Narou novel alert settings"),
                    SubcommandData(SUBCOMMAND_SET_CHANNEL, "Set the channel that receives Narou novel alerts")
                        .addOptions(textChannelOption("The channel used for Narou novel alerts")),
                    SubcommandData(SUBCOMMAND_SET_THRESHOLD, "Set the character growth threshold for alerts")
                        .addOptions(
                            OptionData(
                                OptionType.INTEGER,
                                OPTION_THRESHOLD,
                                "Minimum character growth required before an alert is sent",
                                true
                            ).setMinValue(MINIMUM_THRESHOLD)
                        ),
                    SubcommandData(SUBCOMMAND_DISABLE, "Disable Narou novel alerts for this server")
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

    internal fun getRequiredThreshold(event: SlashCommandInteractionEvent): Long? {
        val threshold = event.getOption(OPTION_THRESHOLD)?.asLong
        if (threshold == null) {
            event.reply("Please provide a length threshold.").setEphemeral(true).queue()
            return null
        }
        if (threshold < MINIMUM_THRESHOLD) {
            event.reply("The length threshold must be at least 50.").setEphemeral(true).queue()
            return null
        }

        return threshold
    }

    private fun initializeBaselines(settings: NarouNovelAlertSettings) {
        val snapshot = narouNovelSnapshotRepository.findById(NarouNovelPollingService.NOVEL_CODE).orElse(null) ?: return
        if (settings.lastAlertedLength == null) {
            settings.lastAlertedLength = snapshot.length
        }
        if (settings.lastAlertedGeneralAllNo == null) {
            settings.lastAlertedGeneralAllNo = snapshot.generalAllNo
        }
    }

    private fun clearAlertConfiguration(guildId: Long) {
        narouNovelAlertSettingsRepository.deleteById(guildId)
        narouNovelPendingAlertRepository.deleteById(guildId)
    }

    private fun showCurrentSettings(event: SlashCommandInteractionEvent, guild: Guild) {
        val settings = narouNovelAlertSettingsRepository.findById(guild.idLong).orElse(null)
        val message = buildString {
            appendLine("Narou novel alert settings for ${guild.name}")
            appendLine()
            appendLine("- Alert channel: ${formatChannel(guild, settings?.channelId)}")
            appendLine("- Character growth threshold: ${settings?.lengthThreshold ?: NarouNovelAlertSettings.DEFAULT_LENGTH_THRESHOLD}")
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
