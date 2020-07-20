package be.duncanc.discordmodbot.data.services

import be.duncanc.discordmodbot.bot.RunBots
import be.duncanc.discordmodbot.data.entities.MuteRole
import be.duncanc.discordmodbot.data.entities.ScheduledUnmute
import be.duncanc.discordmodbot.data.repositories.MuteRolesRepository
import be.duncanc.discordmodbot.data.repositories.ScheduledUnmuteRepository
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.Member
import net.dv8tion.jda.api.entities.Role
import org.springframework.context.annotation.Lazy
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import java.time.OffsetDateTime
import javax.transaction.Transactional

@Service
class ScheduledUnmuteService(
        private val scheduledUnmuteRepository: ScheduledUnmuteRepository,
        private val muteRolesRepository: MuteRolesRepository,
        @Lazy
        private val runBots: RunBots
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

    @Scheduled(fixedRate = 1000 * 60 * 60)
    @Transactional
    fun performUnmute() {
        scheduledUnmuteRepository.findAllByUnmuteDateTimeIsBefore(OffsetDateTime.now()).forEach { scheduledUnmute ->
            runBots.runningBots.forEach { jda ->
                jda.getGuildById(scheduledUnmute.guildId)?.let(getMemberToUnmute(scheduledUnmute))
            }
        }
    }

    private fun getMemberToUnmute(scheduledUnmute: ScheduledUnmute): (Guild) -> Unit {
        return { guild ->
            guild.getMemberById(scheduledUnmute.userId)?.let(getMuteRoleFromRepository(guild, scheduledUnmute))
        }
    }

    private fun getMuteRoleFromRepository(guild: Guild, scheduledUnmute: ScheduledUnmute): (Member) -> Unit {
        return { member ->
            muteRolesRepository.findById(guild.idLong).ifPresent { muteRole ->
                getMuteRoleFromGuild(guild, muteRole, member, scheduledUnmute)
            }
        }
    }

    private fun getMuteRoleFromGuild(guild: Guild, muteRole: MuteRole, member: Member, scheduledUnmute: ScheduledUnmute) {
        guild.getRoleById(muteRole.roleId)?.let(removeMute(guild, member, scheduledUnmute))
    }

    private fun removeMute(guild: Guild, member: Member, scheduledUnmute: ScheduledUnmute): (Role) -> Unit {
        return { role ->
            guild.removeRoleFromMember(member, role).queue {
                scheduledUnmuteRepository.delete(scheduledUnmute)
            }
        }
    }
}
