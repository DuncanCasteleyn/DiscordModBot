package be.duncanc.discordmodbot.moderation

import be.duncanc.discordmodbot.discord.SlashCommand
import be.duncanc.discordmodbot.moderation.persistence.ReportSettings
import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.hooks.ListenerAdapter
import net.dv8tion.jda.api.interactions.InteractionContextType
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions
import net.dv8tion.jda.api.interactions.commands.OptionType
import net.dv8tion.jda.api.interactions.commands.build.Commands
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData
import org.springframework.stereotype.Component

@Component
class ReportSettingsCommand(
    private val reportSettingsService: ReportSettingsService
) : ListenerAdapter(), SlashCommand {
    companion object {
        private const val COMMAND = "reportsettings"
        private const val DESCRIPTION = "Configure message reporting for this server."
        private const val SUBCOMMAND_SHOW = "show"
        private const val SUBCOMMAND_BLOCK_USER = "block-user"
        private const val SUBCOMMAND_ALLOW_USER = "allow-user"
        private const val SUBCOMMAND_SET_URGENT_ROLE = "set-urgent-role"
        private const val SUBCOMMAND_CLEAR_URGENT_ROLE = "clear-urgent-role"
        private const val SUBCOMMAND_TOGGLE = "toggle"
        private const val OPTION_USER = "user"
        private const val OPTION_ROLE = "role"
        private const val BLOCKED_USER_DISPLAY_LIMIT = 20
    }

    override fun onSlashCommandInteraction(event: SlashCommandInteractionEvent) {
        if (event.name != COMMAND) return

        val guild = event.guild
        val member = event.member
        if (guild == null || member == null) {
            event.reply("This command only works in a guild.").setEphemeral(true).queue()
            return
        }

        if (!member.hasPermission(Permission.MANAGE_ROLES)) {
            event.reply("You need manage roles permission to use this command.").setEphemeral(true).queue()
            return
        }

        when (event.subcommandName) {
            null, SUBCOMMAND_SHOW -> showCurrentSettings(event, guild)
            SUBCOMMAND_BLOCK_USER -> blockUser(event, guild.idLong)
            SUBCOMMAND_ALLOW_USER -> allowUser(event, guild.idLong)
            SUBCOMMAND_SET_URGENT_ROLE -> setUrgentRole(event, guild.idLong)
            SUBCOMMAND_CLEAR_URGENT_ROLE -> {
                reportSettingsService.clearUrgentRole(guild.idLong)
                event.reply("Urgent report role cleared. Urgent reports will mention @everyone.")
                    .setEphemeral(true)
                    .queue()
            }

            SUBCOMMAND_TOGGLE -> {
                val enabled = reportSettingsService.toggleReporting(guild.idLong)
                val status = if (enabled) "enabled" else "disabled"
                event.reply("Message reporting is now $status.").setEphemeral(true).queue()
            }

            else -> event.reply("Please choose a valid /reportsettings subcommand.").setEphemeral(true).queue()
        }
    }

    override fun getCommandsData(): List<SlashCommandData> {
        return listOf(
            Commands.slash(COMMAND, DESCRIPTION)
                .setContexts(InteractionContextType.GUILD)
                .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.MANAGE_ROLES))
                .addSubcommands(
                    SubcommandData(SUBCOMMAND_SHOW, "Show current report settings"),
                    SubcommandData(SUBCOMMAND_BLOCK_USER, "Block a user from reporting messages")
                        .addOption(OptionType.USER, OPTION_USER, "The user to block", true),
                    SubcommandData(SUBCOMMAND_ALLOW_USER, "Allow a blocked user to report messages again")
                        .addOption(OptionType.USER, OPTION_USER, "The user to allow", true),
                    SubcommandData(SUBCOMMAND_SET_URGENT_ROLE, "Set the role mentioned by urgent reports")
                        .addOption(OptionType.ROLE, OPTION_ROLE, "The role to mention", true),
                    SubcommandData(SUBCOMMAND_CLEAR_URGENT_ROLE, "Clear the urgent report role"),
                    SubcommandData(SUBCOMMAND_TOGGLE, "Enable or disable message reporting")
                )
        )
    }

    private fun blockUser(event: SlashCommandInteractionEvent, guildId: Long) {
        val user = event.getOption(OPTION_USER)?.asUser
        if (user == null) {
            event.reply("Please choose a user.").setEphemeral(true).queue()
            return
        }

        reportSettingsService.blockUser(guildId, user.idLong)
        event.reply("${user.asMention} can no longer report messages in this server.").setEphemeral(true).queue()
    }

    private fun allowUser(event: SlashCommandInteractionEvent, guildId: Long) {
        val user = event.getOption(OPTION_USER)?.asUser
        if (user == null) {
            event.reply("Please choose a user.").setEphemeral(true).queue()
            return
        }

        reportSettingsService.allowUser(guildId, user.idLong)
        event.reply("${user.asMention} can report messages in this server again.").setEphemeral(true).queue()
    }

    private fun setUrgentRole(event: SlashCommandInteractionEvent, guildId: Long) {
        val role = event.getOption(OPTION_ROLE)?.asRole
        if (role == null) {
            event.reply("Please choose a role.").setEphemeral(true).queue()
            return
        }

        reportSettingsService.setUrgentRole(guildId, role.idLong)
        event.reply("Urgent reports will now mention ${role.asMention}.").setEphemeral(true).queue()
    }

    private fun showCurrentSettings(event: SlashCommandInteractionEvent, guild: Guild) {
        val settings = reportSettingsService.getSettings(guild.idLong)
        val message = buildString {
            appendLine("Report settings for ${guild.name}")
            appendLine()
            appendLine("- Reporting: ${if (settings.enabled) "enabled" else "disabled"}")
            appendLine("- Urgent mention: ${reportSettingsService.getUrgentMention(guild)}")
            appendLine("- Blocked users: ${formatBlockedUsers(settings)}")
        }

        event.reply(message).setEphemeral(true).queue()
    }

    private fun formatBlockedUsers(settings: ReportSettings): String {
        if (settings.blockedUserIds.isEmpty()) {
            return "None"
        }

        val displayedUsers = settings.blockedUserIds
            .take(BLOCKED_USER_DISPLAY_LIMIT)
            .joinToString(", ") { "<@$it>" }
        val remaining = settings.blockedUserIds.size - BLOCKED_USER_DISPLAY_LIMIT
        if (remaining <= 0) {
            return displayedUsers
        }

        return "$displayedUsers, and $remaining more"
    }
}
