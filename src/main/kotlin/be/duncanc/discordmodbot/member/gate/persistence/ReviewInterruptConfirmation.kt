package be.duncanc.discordmodbot.member.gate.persistence

import org.springframework.data.annotation.Id
import org.springframework.data.redis.core.RedisHash

@RedisHash("memberGateReviewInterruptConfirmation", timeToLive = 600)
data class ReviewInterruptConfirmation(
    @Id
    val id: String,
    val guildId: Long,
    val reviewerId: Long,
    val targetReviewerIds: Set<Long>
)
