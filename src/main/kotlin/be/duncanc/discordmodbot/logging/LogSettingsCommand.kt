package be.duncanc.discordmodbot.logging

import be.duncanc.discordmodbot.discord.SlashCommand
import be.duncanc.discordmodbot.logging.persistence.LoggingSettings
import be.duncanc.discordmodbot.logging.persistence.LoggingSettingsRepository
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
import org.springframework.transaction.annotation.Transactional

@Component
class LogSettingsCommand(
    private val loggingSettingsRepository: LoggingSettingsRepository
) : ListenerAdapter(), SlashCommand {
    companion object {
        private const val COMMAND = "logsettings"
        private const val DESCRIPTION = "Adjust server logging settings."

        private const val SUBCOMMAND_SHOW = "show"
        private const val SUBCOMMAND_SET_MOD_CHANNEL = "set-mod-channel"
        private const val SUBCOMMAND_SET_USER_CHANNEL = "set-user-channel"
        private const val SUBCOMMAND_TOGGLE_MESSAGE_UPDATES = "toggle-message-updates"
        private const val SUBCOMMAND_TOGGLE_MESSAGE_DELETES = "toggle-message-deletes"
        private const val SUBCOMMAND_TOGGLE_MEMBER_JOINS = "toggle-member-joins"
        private const val SUBCOMMAND_TOGGLE_MEMBER_LEAVES = "toggle-member-leaves"
        private const val SUBCOMMAND_TOGGLE_MEMBER_BANS = "toggle-member-bans"
        private const val SUBCOMMAND_TOGGLE_MEMBER_UNBANS = "toggle-member-unbans"

        private const val OPTION_CHANNEL = "channel"
    }

    @Transactional
    override fun onGuildLeave(event: GuildLeaveEvent) {
        loggingSettingsRepository.deleteById(event.guild.idLong)
    }

    @Transactional
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

        val guildId = guild.idLong
        val settings = loggingSettingsRepository.findById(guildId).orElse(LoggingSettings(guildId))

        when (event.subcommandName) {
            null, SUBCOMMAND_SHOW -> showCurrentSettings(event, guild, settings)
            SUBCOMMAND_SET_MOD_CHANNEL -> {
                val channel = getRequiredTextChannel(event) ?: return
                settings.modLogChannel = channel.idLong
                loggingSettingsRepository.save(settings)
                event.reply("Moderator log channel set to ${channel.asMention}.").setEphemeral(true).queue()
            }

            SUBCOMMAND_SET_USER_CHANNEL -> {
                val channel = getRequiredTextChannel(event) ?: return
                settings.userLogChannel = channel.idLong
                loggingSettingsRepository.save(settings)
                event.reply("User log channel set to ${channel.asMention}.").setEphemeral(true).queue()
            }

            SUBCOMMAND_TOGGLE_MESSAGE_UPDATES -> {
                settings.logMessageUpdate = !settings.logMessageUpdate
                loggingSettingsRepository.save(settings)
                replyToggleStatus(event, "Message update logging", settings.logMessageUpdate)
            }

            SUBCOMMAND_TOGGLE_MESSAGE_DELETES -> {
                settings.logMessageDelete = !settings.logMessageDelete
                loggingSettingsRepository.save(settings)
                replyToggleStatus(event, "Message delete logging", settings.logMessageDelete)
            }

            SUBCOMMAND_TOGGLE_MEMBER_JOINS -> {
                settings.logMemberJoin = !settings.logMemberJoin
                loggingSettingsRepository.save(settings)
                replyToggleStatus(event, "Member join logging", settings.logMemberJoin)
            }

            SUBCOMMAND_TOGGLE_MEMBER_LEAVES -> {
                settings.logMemberLeave = !settings.logMemberLeave
                loggingSettingsRepository.save(settings)
                replyToggleStatus(event, "Member leave logging", settings.logMemberLeave)
            }

            SUBCOMMAND_TOGGLE_MEMBER_BANS -> {
                settings.logMemberBan = !settings.logMemberBan
                loggingSettingsRepository.save(settings)
                replyToggleStatus(event, "Member ban logging", settings.logMemberBan)
            }

            SUBCOMMAND_TOGGLE_MEMBER_UNBANS -> {
                settings.logMemberRemoveBan = !settings.logMemberRemoveBan
                loggingSettingsRepository.save(settings)
                replyToggleStatus(event, "Member unban logging", settings.logMemberRemoveBan)
            }

            else -> event.reply("Please choose a valid /logsettings subcommand.").setEphemeral(true).queue()
        }
    }

    override fun getCommandsData(): List<SlashCommandData> {
        return listOf(
            Commands.slash(COMMAND, DESCRIPTION)
                .addSubcommands(
                    SubcommandData(SUBCOMMAND_SHOW, "Show the current logging settings"),
                    SubcommandData(SUBCOMMAND_SET_MOD_CHANNEL, "Set the moderator logging channel")
                        .addOptions(textChannelOption("The channel used for moderator logs")),
                    SubcommandData(SUBCOMMAND_SET_USER_CHANNEL, "Set the user logging channel")
                        .addOptions(textChannelOption("The channel used for user logs")),
                    SubcommandData(SUBCOMMAND_TOGGLE_MESSAGE_UPDATES, "Enable or disable edited message logging"),
                    SubcommandData(SUBCOMMAND_TOGGLE_MESSAGE_DELETES, "Enable or disable deleted message logging"),
                    SubcommandData(SUBCOMMAND_TOGGLE_MEMBER_JOINS, "Enable or disable member join logging"),
                    SubcommandData(SUBCOMMAND_TOGGLE_MEMBER_LEAVES, "Enable or disable member leave logging"),
                    SubcommandData(SUBCOMMAND_TOGGLE_MEMBER_BANS, "Enable or disable member ban logging"),
                    SubcommandData(SUBCOMMAND_TOGGLE_MEMBER_UNBANS, "Enable or disable member unban logging")
                )
                .setContexts(InteractionContextType.GUILD)
                .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.MANAGE_CHANNEL))
        )
    }

    private fun showCurrentSettings(
        event: SlashCommandInteractionEvent,
        guild: Guild,
        settings: LoggingSettings
    ) {
        val message = buildString {
            appendLine("Logging settings for ${guild.name}")
            appendLine()
            appendLine("- Moderator log channel: ${formatChannel(guild, settings.modLogChannel, "Not configured")}")
            appendLine(
                "- User log channel: ${formatChannel(guild, settings.userLogChannel, "Using moderator log channel")}"
            )
            appendLine("- Edited messages: ${formatStatus(settings.logMessageUpdate)}")
            appendLine("- Deleted messages: ${formatStatus(settings.logMessageDelete)}")
            appendLine("- Member joins: ${formatStatus(settings.logMemberJoin)}")
            appendLine("- Member leaves: ${formatStatus(settings.logMemberLeave)}")
            appendLine("- Member bans: ${formatStatus(settings.logMemberBan)}")
            appendLine("- Member unbans: ${formatStatus(settings.logMemberRemoveBan)}")
        }

        event.reply(message).setEphemeral(true).queue()
    }

    private fun formatChannel(guild: Guild, channelId: Long?, nullMessage: String): String {
        if (channelId == null) {
            return nullMessage
        }

        return guild.getTextChannelById(channelId)?.asMention ?: "Channel not found (ID: $channelId)"
    }

    private fun formatStatus(enabled: Boolean): String {
        return if (enabled) "enabled" else "disabled"
    }

    internal fun getRequiredTextChannel(event: SlashCommandInteractionEvent): TextChannel? {
        val channel = event.getOption(OPTION_CHANNEL)?.asChannel?.asTextChannel()
        if (channel == null) {
            event.reply("Please mention a text channel.").setEphemeral(true).queue()
            return null
        }

        return channel
    }

    private fun replyToggleStatus(event: SlashCommandInteractionEvent, label: String, enabled: Boolean) {
        event.reply("$label is now ${formatStatus(enabled)}.").setEphemeral(true).queue()
    }

    private fun textChannelOption(description: String): OptionData {
        return OptionData(OptionType.CHANNEL, OPTION_CHANNEL, description, true)
            .setChannelTypes(ChannelType.TEXT)
    }
}
