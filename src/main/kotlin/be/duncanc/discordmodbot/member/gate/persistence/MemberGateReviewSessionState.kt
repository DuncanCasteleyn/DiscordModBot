package be.duncanc.discordmodbot.member.gate.persistence

import org.springframework.data.annotation.Id
import org.springframework.data.redis.core.RedisHash

@RedisHash("memberGateReviewSession", timeToLive = 1800)
data class MemberGateReviewSessionState(
    @Id
    val id: String,
    val guildId: Long,
    val reviewerId: Long,
    val oldestPendingUserId: Long,
    val pendingUserIds: List<Long>
)
