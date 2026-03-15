package be.duncanc.discordmodbot.moderation

import be.duncanc.discordmodbot.moderation.persistence.GuildWarnPoint
import be.duncanc.discordmodbot.moderation.persistence.GuildWarnPointsRepository

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.OffsetDateTime
import java.util.*

@Service
class GuildWarnPointsService(private val guildWarnPointsRepository: GuildWarnPointsRepository) {

    @Transactional(readOnly = true)
    fun userHasGuildWarnings(guildId: Long, userId: Long): Boolean {
        return guildWarnPointsRepository.existsByGuildIdAndUserId(guildId, userId)
    }

    @Transactional(readOnly = true)
    fun getGuildWarningsFromUser(guildId: Long, userId: Long): Collection<GuildWarnPoint> {
        return guildWarnPointsRepository.findAllByGuildIdAndUserId(guildId, userId)
    }

    @Transactional
    fun revokePoint(id: UUID) {
        guildWarnPointsRepository.deleteAllById(id)
    }

    @Transactional(readOnly = true)
    fun getWarningById(id: UUID): GuildWarnPoint? {
        return guildWarnPointsRepository.findById(id)
    }

    @Transactional
    fun addWarnPoint(
        userId: Long,
        guildId: Long,
        points: Int,
        creatorId: Long,
        reason: String,
        expireDate: OffsetDateTime
    ): GuildWarnPoint {
        val guildWarnPoint = GuildWarnPoint(
            userId = userId,
            guildId = guildId,
            points = points,
            creatorId = creatorId,
            reason = reason,
            expireDate = expireDate
        )
        return guildWarnPointsRepository.save(guildWarnPoint)
    }

    @Transactional(readOnly = true)
    fun getActivePointsCount(guildId: Long, userId: Long): Int {
        return guildWarnPointsRepository.countAllByGuildIdAndUserIdAndExpireDateAfter(
            guildId, userId, OffsetDateTime.now()
        )
    }

    @Transactional(readOnly = true)
    fun getActiveWarnings(guildId: Long, userId: Long): Collection<GuildWarnPoint> {
        return guildWarnPointsRepository.findAllByGuildIdAndUserIdAndExpireDateAfter(
            guildId,
            userId,
            OffsetDateTime.now()
        )
    }

}
