package be.duncanc.discordmodbot.member.gate.persistence

import org.springframework.data.annotation.Id
import org.springframework.data.redis.core.RedisHash

@RedisHash("memberGateReviewPrompt", timeToLive = 172800)
data class MemberGateReviewPrompt(
    @Id
    val id: String,
    val guildId: Long,
    val userId: Long,
    val messageId: Long
) {
    companion object {
        fun createId(guildId: Long, userId: Long): String = "$guildId:$userId"
    }
}
