package be.duncanc.discordmodbot.moderation

import be.duncanc.discordmodbot.discord.SlashCommand
import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.Role
import net.dv8tion.jda.api.events.guild.GuildLeaveEvent
import net.dv8tion.jda.api.events.guild.member.GuildMemberJoinEvent
import net.dv8tion.jda.api.events.guild.member.GuildMemberRemoveEvent
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.events.role.RoleDeleteEvent
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
class StickyRolesCommand(
    private val stickyRoleService: StickyRoleService
) : ListenerAdapter(), SlashCommand {
    companion object {
        private const val COMMAND = "stickyroles"
        private const val DESCRIPTION = "Configure which roles are restored when users rejoin."

        private const val SUBCOMMAND_SHOW = "show"
        private const val SUBCOMMAND_ADD = "add"
        private const val SUBCOMMAND_REMOVE = "remove"
        private const val SUBCOMMAND_CLEAR = "clear"

        private const val OPTION_ROLE = "role"
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

        if (!member.hasPermission(Permission.MANAGE_ROLES)) {
            event.reply("You need manage roles permission to use this command.").setEphemeral(true).queue()
            return
        }

        when (event.subcommandName) {
            null, SUBCOMMAND_SHOW -> showCurrentSettings(event, guild)
            SUBCOMMAND_ADD -> addRole(event, guild)
            SUBCOMMAND_REMOVE -> removeRole(event, guild)
            SUBCOMMAND_CLEAR -> {
                stickyRoleService.clearConfiguredRoles(guild.idLong)
                event.reply("Sticky roles cleared.").setEphemeral(true).queue()
            }

            else -> event.reply("Please choose a valid /stickyroles subcommand.").setEphemeral(true).queue()
        }
    }

    override fun onGuildMemberRemove(event: GuildMemberRemoveEvent) {
        val member = event.member ?: return
        stickyRoleService.captureRolesOnLeave(event.guild.idLong, event.user.idLong, member.roles.map { it.idLong })
    }

    override fun onGuildMemberJoin(event: GuildMemberJoinEvent) {
        stickyRoleService.restoreRolesOnJoin(event.guild, event.member)
    }

    override fun onRoleDelete(event: RoleDeleteEvent) {
        stickyRoleService.removeDeletedRole(event.guild.idLong, event.role.idLong)
    }

    override fun onGuildLeave(event: GuildLeaveEvent) {
        stickyRoleService.clearGuildState(event.guild.idLong)
    }

    override fun getCommandsData(): List<SlashCommandData> {
        return listOf(
            Commands.slash(COMMAND, DESCRIPTION)
                .setContexts(InteractionContextType.GUILD)
                .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.MANAGE_ROLES))
                .addSubcommands(
                    SubcommandData(SUBCOMMAND_SHOW, "Show the configured sticky roles"),
                    SubcommandData(SUBCOMMAND_ADD, "Add a sticky role")
                        .addOptions(OptionData(OptionType.ROLE, OPTION_ROLE, "Role to restore on rejoin", true)),
                    SubcommandData(SUBCOMMAND_REMOVE, "Remove a sticky role")
                        .addOptions(OptionData(OptionType.ROLE, OPTION_ROLE, "Role to stop restoring", true)),
                    SubcommandData(SUBCOMMAND_CLEAR, "Clear all sticky role configuration")
                )
        )
    }

    private fun showCurrentSettings(event: SlashCommandInteractionEvent, guild: Guild) {
        val configuredRoles = stickyRoleService.getConfiguredRoleIds(guild.idLong)
        val message = buildString {
            appendLine("Sticky role settings for ${guild.name}")
            appendLine()
            if (configuredRoles.isEmpty()) {
                appendLine("- Sticky roles: Not configured")
            } else {
                appendLine("- Sticky roles:")
                configuredRoles
                    .map { roleId -> guild.getRoleById(roleId)?.asMention ?: "Missing role (ID: $roleId)" }
                    .sorted()
                    .forEach { appendLine("  $it") }
            }
        }

        event.reply(message).setEphemeral(true).queue()
    }

    private fun addRole(event: SlashCommandInteractionEvent, guild: Guild) {
        val role = getRequiredRole(event) ?: return
        val validationError = validateStickyRole(role)
        if (validationError != null) {
            event.reply(validationError).setEphemeral(true).queue()
            return
        }

        stickyRoleService.addConfiguredRole(guild.idLong, role.idLong)
        event.reply("Added ${role.asMention} to sticky roles.").setEphemeral(true).queue()
    }

    private fun removeRole(event: SlashCommandInteractionEvent, guild: Guild) {
        val role = getRequiredRole(event) ?: return
        stickyRoleService.removeConfiguredRole(guild.idLong, role.idLong)
        event.reply("Removed ${role.asMention} from sticky roles.").setEphemeral(true).queue()
    }

    internal fun getRequiredRole(event: SlashCommandInteractionEvent): Role? {
        val role = event.getOption(OPTION_ROLE)?.asRole
        if (role == null) {
            event.reply("Please choose a role.").setEphemeral(true).queue()
            return null
        }

        return role
    }

    private fun validateStickyRole(role: Role): String? {
        return when {
            role.isPublicRole -> "The @everyone role cannot be made sticky."
            role.isManaged -> "Managed roles cannot be made sticky."
            !role.guild.selfMember.hasPermission(Permission.MANAGE_ROLES) -> "I need manage roles permission to restore sticky roles."
            !role.guild.selfMember.canInteract(role) -> "I can't restore that role because it is above my highest role."
            else -> null
        }
    }
}
