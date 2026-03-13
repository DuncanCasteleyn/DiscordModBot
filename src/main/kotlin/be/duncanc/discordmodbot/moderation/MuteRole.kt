package be.duncanc.discordmodbot.moderation

import be.duncanc.discordmodbot.discord.CommandModule
import be.duncanc.discordmodbot.discord.nicknameAndUsername
import be.duncanc.discordmodbot.logging.GuildLogger
import be.duncanc.discordmodbot.moderation.persistence.MuteRole as MuteRoleEntity
import be.duncanc.discordmodbot.moderation.persistence.MuteRolesRepository
import java.awt.Color
import java.util.concurrent.TimeUnit
import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.Role
import net.dv8tion.jda.api.events.guild.member.GuildMemberJoinEvent
import net.dv8tion.jda.api.events.guild.member.GuildMemberRemoveEvent
import net.dv8tion.jda.api.events.guild.member.GuildMemberRoleAddEvent
import net.dv8tion.jda.api.events.guild.member.GuildMemberRoleRemoveEvent
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import net.dv8tion.jda.api.events.role.RoleDeleteEvent
import net.dv8tion.jda.api.exceptions.PermissionException
import net.dv8tion.jda.api.Permission
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
@Transactional
class MuteRole
@Autowired constructor(
    private val muteRolesRepository: MuteRolesRepository,
    private val guildLogger: GuildLogger
) : CommandModule(
    arrayOf("MuteRole"),
    "[Name of the mute role or nothing to remove the role]",
    "This command allows you to set the mute role for a guild/server"
) {

    override fun commandExec(event: MessageReceivedEvent, command: String, arguments: String?) {
        if (event.member?.hasPermission(Permission.MANAGE_ROLES) != true) {
            throw PermissionException("You do not have sufficient permissions to set the mute role for this server")
        }

        val guildId = event.guild.idLong
        if (arguments == null) {
            muteRolesRepository.deleteById(guildId)
            event.channel.sendMessage("Mute role has been removed.")
                .queue { it.delete().queueAfter(1, TimeUnit.MINUTES) }
        } else {
            try {
                muteRolesRepository.save(
                    MuteRoleEntity(
                        guildId,
                        event.guild.getRolesByName(arguments, false)[0].idLong
                    )
                )
                event.channel.sendMessage("Role has been set as mute role.")
                    .queue { it.delete().queueAfter(1, TimeUnit.MINUTES) }
            } catch (exception: IndexOutOfBoundsException) {
                throw IllegalArgumentException("Couldn't find any roles with that name.", exception)
            }
        }
    }

    override fun onRoleDelete(event: RoleDeleteEvent) {
        muteRolesRepository.deleteByRoleIdAndGuildId(event.role.idLong, event.guild.idLong)
    }

    @Transactional(readOnly = true)
    fun getMuteRole(guild: Guild): Role {
        val roleId = muteRolesRepository.findById(guild.idLong)
            .orElse(null)?.roleId
            ?: throw IllegalStateException("This guild does not have a mute role set up.")

        return guild.getRoleById(roleId)!!
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
}
