package be.duncanc.discordmodbot.data.services

import be.duncanc.discordmodbot.bot.services.GuildLogger
import be.duncanc.discordmodbot.bot.services.GuildLogger.LogTypeAction.MODERATOR
import be.duncanc.discordmodbot.bot.utils.nicknameAndUsername
import be.duncanc.discordmodbot.data.entities.MuteRole
import be.duncanc.discordmodbot.data.entities.ScheduledUnmute
import be.duncanc.discordmodbot.data.repositories.jpa.MuteRolesRepository
import be.duncanc.discordmodbot.data.repositories.jpa.ScheduledUnmuteRepository
import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.Member
import net.dv8tion.jda.api.entities.Role
import org.springframework.context.annotation.Lazy
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import java.awt.Color
import java.time.OffsetDateTime
import javax.transaction.Transactional

@Service
class ScheduledUnmuteService(
    private val scheduledUnmuteRepository: ScheduledUnmuteRepository,
    private val muteRolesRepository: MuteRolesRepository,
    private val guildLogger: GuildLogger,
    @Lazy
    private val jda: JDA
) {
    @Transactional
    fun planUnmute(guildId: Long, userId: Long, unmuteDateTime: OffsetDateTime) {
        if (unmuteDateTime.isAfter(OffsetDateTime.now().plusYears(1))) {
            throw IllegalArgumentException("A mute can't take longer than 1 year")
        }
        if (unmuteDateTime.isBefore(OffsetDateTime.now())) {
            throw IllegalArgumentException("An unmute should not be planned in the past")
        }
        if (unmuteDateTime.isBefore(OffsetDateTime.now().plusMinutes(30))) {
            throw IllegalArgumentException("An unmute should be planned at least more than 30 minutes in the future")
        }

        val scheduledUnmute = ScheduledUnmute(guildId, userId, unmuteDateTime)
        scheduledUnmuteRepository.save(scheduledUnmute)
    }

    @Scheduled(cron = "0 0/20 * * * *")
    @Transactional
    fun performUnmute() {
        scheduledUnmuteRepository.findAllByUnmuteDateTimeIsBefore(OffsetDateTime.now()).forEach { scheduledUnmute ->
            jda.getGuildById(scheduledUnmute.guildId)?.let { guild ->
                unmuteMembers(scheduledUnmute, guild)
            }
        }
    }

    private fun unmuteMembers(scheduledUnmute: ScheduledUnmute, guild: Guild) {
        val member = guild.getMemberById(scheduledUnmute.userId)
        if (member != null) {
            unmuteMember(guild, scheduledUnmute, member)
        } else {
            removeMuteFromDb(scheduledUnmute, guild)
        }
    }

    private fun removeMuteFromDb(scheduledUnmute: ScheduledUnmute, guild: Guild) {
        muteRolesRepository.findById(guild.idLong).ifPresent { muteRole ->
            val userId = scheduledUnmute.userId
            muteRole.mutedUsers.remove(userId)
            muteRolesRepository.save(muteRole)
            scheduledUnmuteRepository.delete(scheduledUnmute)
            guild.jda.retrieveUserById(userId).queue { user ->
                val logEmbed = EmbedBuilder()
                    .setColor(Color.green)
                    .setTitle("User unmuted")
                    .addField("User", user.name, true)
                    .addField("Reason", "Mute expired", false)

                guildLogger.log(logEmbed, user, guild, actionType = MODERATOR)
            }

        }
    }

    private fun unmuteMember(guild: Guild, scheduledUnmute: ScheduledUnmute, member: Member) {
        muteRolesRepository.findById(guild.idLong).ifPresent { muteRole ->
            removeUserFromMuteRole(guild, muteRole, member, scheduledUnmute)
        }
    }

    private fun removeUserFromMuteRole(
        guild: Guild,
        muteRole: MuteRole,
        member: Member,
        scheduledUnmute: ScheduledUnmute
    ) {
        guild.getRoleById(muteRole.roleId)?.let { role ->
            removeMute(guild, member, scheduledUnmute, role)
        }
    }

    private fun removeMute(guild: Guild, member: Member, scheduledUnmute: ScheduledUnmute, role: Role) {
        guild.removeRoleFromMember(member, role).queue {
            scheduledUnmuteRepository.delete(scheduledUnmute)
            logUnmute(guild, member)
            informUserIsUnmuted(member)
        }
    }

    private fun logUnmute(guild: Guild, member: Member) {
        val logEmbed = EmbedBuilder()
            .setColor(Color.green)
            .setTitle("User unmuted")
            .addField("User", member.nicknameAndUsername, true)
            .addField("Reason", "Mute expired", false)

        guildLogger.log(logEmbed, member.user, guild, actionType = MODERATOR)
    }

    private fun informUserIsUnmuted(member: Member) {
        val selfUser = member.jda.selfUser
        val guild = member.guild
        val embed = EmbedBuilder()
            .setColor(Color.green)
            .setAuthor(
                guild.getMember(selfUser)?.nicknameAndUsername ?: selfUser.name,
                null,
                selfUser.effectiveAvatarUrl
            )
            .setTitle(guild.name + ": Your mute has been removed")
            .addField("Reason", "Mute expired", false)
            .build()
        member.user.openPrivateChannel().queue {
            it.sendMessageEmbeds(embed).queue()
        }
    }
}
