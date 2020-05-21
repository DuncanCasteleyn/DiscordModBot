package be.duncanc.discordmodbot.data.services

import be.duncanc.discordmodbot.bot.RunBots
import be.duncanc.discordmodbot.data.entities.ScheduledUnmute
import be.duncanc.discordmodbot.data.repositories.MuteRolesRepository
import be.duncanc.discordmodbot.data.repositories.ScheduledUnmuteRepository
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import java.time.OffsetDateTime

@Service
class ScheduledUnmuteService(
        private val scheduledUnmuteRepository: ScheduledUnmuteRepository,
        private val muteRolesRepository: MuteRolesRepository,
        private val runBots: RunBots
) {
    fun planUnmute(guildId: Long, userId: Long, unmuteDateTime: OffsetDateTime) {
        val scheduledUnmute = ScheduledUnmute(guildId, userId, unmuteDateTime)
        scheduledUnmuteRepository.save(scheduledUnmute)
    }

    @Scheduled(fixedRate = 1000 * 60 * 60)
    fun performUnmute() {
        scheduledUnmuteRepository.findByUnmuteDateTimeAfter(OffsetDateTime.now()).forEach { scheduleUnmute ->
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
