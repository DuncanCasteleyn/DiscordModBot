package be.duncanc.discordmodbot.member.gate.persistence

import org.springframework.data.annotation.Id
import org.springframework.data.redis.core.RedisHash
import java.time.Instant

@RedisHash("memberGateReviewSession", timeToLive = 43200)
data class ReviewSessionState(
    @Id
    val id: String,
    val guildId: Long,
    val reviewerId: Long,
    val oldestPendingUserId: Long,
    val pendingUserIds: List<Long>,
    val approvedCount: Int = 0,
    val rejectedCount: Int = 0,
    val manualActionCount: Int = 0,
    val updatedAt: Instant = Instant.EPOCH
)
