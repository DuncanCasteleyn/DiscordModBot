package be.duncanc.discordmodbot.moderation.persistence


import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.time.OffsetDateTime

@Repository
interface ScheduledUnmuteRepository : JpaRepository<ScheduledUnmute, ScheduledUnmute.ScheduledUnmuteId> {
    fun existsByGuildIdAndUserId(guildId: Long, userId: Long): Boolean

    fun findAllByUnmuteDateTimeIsBefore(unmuteDateTime: OffsetDateTime): Iterable<ScheduledUnmute>
}
