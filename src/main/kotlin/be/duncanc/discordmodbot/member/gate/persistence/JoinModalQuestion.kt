package be.duncanc.discordmodbot.member.gate.persistence

import org.springframework.data.annotation.Id
import org.springframework.data.redis.core.RedisHash

@RedisHash("memberGateModalQuestion", timeToLive = 600)
data class JoinModalQuestion(
    @Id
    val id: String,
    val guildId: Long,
    val userId: Long,
    val question: String
) {
    companion object {
        fun createId(guildId: Long, userId: Long): String = "$guildId:$userId"
    }
}
