package be.duncanc.discordmodbot.moderation

import be.duncanc.discordmodbot.discord.SlashCommand
import be.duncanc.discordmodbot.discord.nicknameAndUsername
import be.duncanc.discordmodbot.logging.GuildLogger
import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.Role
import net.dv8tion.jda.api.events.guild.member.GuildMemberJoinEvent
import net.dv8tion.jda.api.events.guild.member.GuildMemberRemoveEvent
import net.dv8tion.jda.api.events.guild.member.GuildMemberRoleAddEvent
import net.dv8tion.jda.api.events.guild.member.GuildMemberRoleRemoveEvent
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.events.role.RoleDeleteEvent
import net.dv8tion.jda.api.hooks.ListenerAdapter
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions
import net.dv8tion.jda.api.interactions.commands.OptionType
import net.dv8tion.jda.api.interactions.commands.build.Commands
import net.dv8tion.jda.api.interactions.commands.build.OptionData
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.awt.Color

@Component
class MuteRoleCommandAndEventsListener(
    private val muteService: MuteService,
    private val guildLogger: GuildLogger
) : ListenerAdapter(), SlashCommand {
    companion object {
        private const val COMMAND = "muterole"
        private const val DESCRIPTION = "Set or remove the mute role for this server."
        private const val OPTION_ROLE = "role"
    }

    override fun onSlashCommandInteraction(event: SlashCommandInteractionEvent) {
        if (event.name != COMMAND) return

        val member = event.member
        if (member == null) {
            event.reply("This command only works in a guild.").setEphemeral(true).queue()
            return
        }

        if (!member.hasPermission(Permission.MANAGE_ROLES)) {
            event.reply("You need manage roles permission to set the mute role.").setEphemeral(true).queue()
            return
        }

        val guild = member.guild
        val guildId = guild.idLong
        val roleOption = event.getOption(OPTION_ROLE)

        if (roleOption == null) {
            muteService.removeMuteRole(guildId)
            event.reply("Mute role has been removed.").setEphemeral(true).queue()
        } else {
            val role = roleOption.asRole
            muteService.setMuteRole(guildId, role.idLong)
            event.reply("Role ${role.asMention} has been set as the mute role.").setEphemeral(true).queue()
        }
    }

    @Transactional(readOnly = true)
    fun getMuteRole(guild: Guild): Role {
        val roleId = muteService.getMuteRoleId(guild.idLong)
            ?: throw IllegalStateException("This guild does not have a mute role set up.")

        return guild.getRoleById(roleId)!!
    }

    override fun onRoleDelete(event: RoleDeleteEvent) {
        muteService.deleteMuteRoleByRoleId(event.guild.idLong, event.role.idLong)
    }

    override fun onGuildMemberRoleAdd(event: GuildMemberRoleAddEvent) {
        val muteRoleId = muteService.getMuteRoleId(event.guild.idLong) ?: return
        if (event.roles.any { it.idLong == muteRoleId }) {
            muteService.addMutedUser(event.guild.idLong, event.user.idLong)
        }
    }

    override fun onGuildMemberRoleRemove(event: GuildMemberRoleRemoveEvent) {
        val muteRoleId = muteService.getMuteRoleId(event.guild.idLong) ?: return
        if (event.roles.any { it.idLong == muteRoleId }) {
            muteService.removeMutedUser(event.guild.idLong, event.user.idLong)
        }
    }

    override fun onGuildMemberRemove(event: GuildMemberRemoveEvent) {
        val member = event.member ?: return
        val muteRoleId = muteService.getMuteRoleId(event.guild.idLong) ?: return
        val hasMuteRole = member.roles.any { it.idLong == muteRoleId }
        muteService.muteOrUnmuteUser(event.guild.idLong, muteRoleId, event.user.idLong, hasMuteRole)
    }

    override fun onGuildMemberJoin(event: GuildMemberJoinEvent) {
        val muteRoleId = muteService.getMuteRoleId(event.guild.idLong) ?: return
        if (muteService.isUserMuted(event.guild.idLong, event.user.idLong)) {
            event.guild.addRoleToMember(event.member, event.guild.getRoleById(muteRoleId)!!).queue()
            val logEmbed = EmbedBuilder()
                .setColor(Color.YELLOW)
                .setTitle("User automatically muted")
                .addField("User", event.member.nicknameAndUsername, true)
                .addField("Reason", "Previously muted before leaving the server", false)
            guildLogger.log(
                guild = event.guild,
                associatedUser = event.user,
                logEmbed = logEmbed,
                actionType = GuildLogger.LogTypeAction.MODERATOR
            )
        }
    }

    override fun getCommandsData(): List<SlashCommandData> {
        return listOf(
            Commands.slash(COMMAND, DESCRIPTION)
                .addOptions(
                    OptionData(
                        OptionType.ROLE,
                        OPTION_ROLE,
                        "The role to use as mute role (omit to remove)"
                    ).setRequired(false)
                )
                .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.MANAGE_ROLES))
        )
    }
}
