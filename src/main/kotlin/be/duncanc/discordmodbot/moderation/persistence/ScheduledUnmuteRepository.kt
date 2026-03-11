package be.duncanc.discordmodbot.moderation.persistence


import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.time.OffsetDateTime

@Repository
interface ScheduledUnmuteRepository : JpaRepository<ScheduledUnmute, ScheduledUnmute.ScheduledUnmuteId> {
    fun findAllByUnmuteDateTimeIsBefore(unmuteDateTime: OffsetDateTime): Iterable<ScheduledUnmute>
}
