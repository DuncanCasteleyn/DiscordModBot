package be.duncanc.discordmodbot.moderation.persistence

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.time.OffsetDateTime

@Repository
interface TrapChannelUnbanRepository : JpaRepository<TrapChannelUnban, TrapChannelUnban.TrapChannelUnbanId> {
    fun findAllByUnbanAtLessThanEqualAndGuildIdIn(
        unbanAt: OffsetDateTime,
        guildIds: Collection<Long>
    ): List<TrapChannelUnban>

    fun deleteAllByGuildId(guildId: Long)
}
