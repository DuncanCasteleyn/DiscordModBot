package be.duncanc.discordmodbot.data.repositories

import be.duncanc.discordmodbot.data.entities.ScheduledUnmute
import org.springframework.data.repository.CrudRepository
import org.springframework.stereotype.Repository
import java.time.OffsetDateTime

@Repository
interface ScheduledUnmuteRepository : CrudRepository<ScheduledUnmute, ScheduledUnmute.ScheduledUnmuteId> {
    fun findAllByUnmuteDateTimeIsBefore(unmuteDateTime: OffsetDateTime): Iterable<ScheduledUnmute>
}
