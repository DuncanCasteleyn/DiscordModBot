package be.duncanc.discordmodbot.moderation.persistence

import java.time.OffsetDateTime
import java.util.*
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface GuildWarnPointsRepository : JpaRepository<GuildWarnPoint, GuildWarnPoint.GuildWarnPointId> {

    fun countAllByGuildIdAndUserIdAndExpireDateAfter(guildId: Long, userId: Long, expireDate: OffsetDateTime): Int

    fun findAllByGuildIdAndExpireDateAfter(guildId: Long, expireDate: OffsetDateTime): Collection<GuildWarnPoint>

    fun findAllByGuildIdAndUserId(guildId: Long, userId: Long): Collection<GuildWarnPoint>

    fun findAllByGuildIdAndUserIdAndExpireDateAfter(
        guildId: Long,
        userId: Long,
        expireDate: OffsetDateTime
    ): Collection<GuildWarnPoint>

    fun deleteAllById(id: UUID)

    fun existsByGuildIdAndUserId(guildId: Long, userId: Long): Boolean
}
