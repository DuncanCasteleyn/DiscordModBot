package be.duncanc.discordmodbot.data.repositories.jpa

import be.duncanc.discordmodbot.data.entities.ScheduledUnmute
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.time.OffsetDateTime

@Repository
interface ScheduledUnmuteRepository : JpaRepository<ScheduledUnmute, ScheduledUnmute.ScheduledUnmuteId> {
    fun findAllByUnmuteDateTimeIsBefore(unmuteDateTime: OffsetDateTime): Iterable<ScheduledUnmute>
}
