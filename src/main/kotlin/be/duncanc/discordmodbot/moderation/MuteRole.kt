package be.duncanc.discordmodbot.moderation

import be.duncanc.discordmodbot.discord.SlashCommand
import be.duncanc.discordmodbot.discord.nicknameAndUsername
import be.duncanc.discordmodbot.logging.GuildLogger
import be.duncanc.discordmodbot.moderation.persistence.MuteRolesRepository
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
import be.duncanc.discordmodbot.moderation.persistence.MuteRole as MuteRoleEntity

@Component
@Transactional
class MuteRole(
    private val muteRolesRepository: MuteRolesRepository,
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

        val guild = event.guild!!
        val guildId = guild.idLong
        val roleOption = event.getOption(OPTION_ROLE)

        if (roleOption == null) {
            muteRolesRepository.deleteById(guildId)
            event.reply("Mute role has been removed.").setEphemeral(true).queue()
        } else {
            val role = roleOption.asRole
            muteRolesRepository.save(MuteRoleEntity(guildId, role.idLong))
            event.reply("Role ${role.asMention} has been set as the mute role.").setEphemeral(true).queue()
        }
    }

    @Transactional(readOnly = true)
    fun getMuteRole(guild: Guild): Role {
        val roleId = muteRolesRepository.findById(guild.idLong)
            .orElse(null)?.roleId
            ?: throw IllegalStateException("This guild does not have a mute role set up.")

        return guild.getRoleById(roleId)!!
    }

    override fun onRoleDelete(event: RoleDeleteEvent) {
        muteRolesRepository.deleteByRoleIdAndGuildId(event.role.idLong, event.guild.idLong)
    }

    override fun onGuildMemberRoleAdd(event: GuildMemberRoleAddEvent) {
        val muteRole = muteRolesRepository.findById(event.guild.idLong)
            .orElse(null)
        if (!(muteRole == null || !event.roles.contains(muteRole.roleId.let { event.guild.getRoleById(it) }))) {
            muteRole.mutedUsers.add(event.user.idLong)
            muteRolesRepository.save(muteRole)
        }
    }

    override fun onGuildMemberRoleRemove(event: GuildMemberRoleRemoveEvent) {
        val muteRole = muteRolesRepository.findById(event.guild.idLong)
            .orElse(null)
        if (muteRole != null && event.roles.contains(muteRole.roleId.let { event.guild.getRoleById(it) })) {
            muteRole.mutedUsers.remove(event.user.idLong)
            muteRolesRepository.save(muteRole)
        }
    }

    override fun onGuildMemberRemove(event: GuildMemberRemoveEvent) {
        val member = event.member
        val muteRole = muteRolesRepository.findById(event.guild.idLong)
            .orElse(null)
        if (muteRole != null && member != null) {
            if (member.roles.contains(muteRole.roleId.let { event.guild.getRoleById(it) })) {
                muteRole.mutedUsers.add(event.user.idLong)
                muteRolesRepository.save(muteRole)
            } else {
                muteRole.mutedUsers.remove(event.user.idLong)
                muteRolesRepository.save(muteRole)
            }
        }
    }

    override fun onGuildMemberJoin(event: GuildMemberJoinEvent) {
        val muteRole = muteRolesRepository.findById(event.guild.idLong)
            .orElse(null)
        if (muteRole?.roleId != null && muteRole.mutedUsers.contains(event.user.idLong)) {
            event.guild.addRoleToMember(event.member, event.guild.getRoleById(muteRole.roleId)!!).queue()
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
