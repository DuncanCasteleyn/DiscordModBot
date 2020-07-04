package be.duncanc.discordmodbot.data.services

import be.duncanc.discordmodbot.bot.RunBots
import be.duncanc.discordmodbot.data.entities.ScheduledUnmute
import be.duncanc.discordmodbot.data.repositories.MuteRolesRepository
import be.duncanc.discordmodbot.data.repositories.ScheduledUnmuteRepository
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
        scheduledUnmuteRepository.findAllByUnmuteDateTimeIsBefore(OffsetDateTime.now()).forEach { scheduleUnmute ->
            runBots.runningBots.forEach { jda ->
                val guild = jda.getGuildById(scheduleUnmute.guildId)
                if (guild != null) {
                    muteRolesRepository.findById(guild.idLong).ifPresent { muteRole ->
                        guild.getRoleById(muteRole.roleId)?.let { role ->
                            guild.removeRoleFromMember(scheduleUnmute.userId, role).queue {
                                scheduledUnmuteRepository.delete(scheduleUnmute)
                            }
                        }
                    }
                }
            }
        }
    }
}
