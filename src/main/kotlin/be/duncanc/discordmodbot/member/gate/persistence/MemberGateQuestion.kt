package be.duncanc.discordmodbot.member.gate.persistence


import org.springframework.data.annotation.Id
import org.springframework.data.redis.core.RedisHash

@RedisHash("memberGateQuestion", timeToLive = 172800)
data class MemberGateQuestion(
    @Id
    val id: String,
    val userId: Long,
    val question: String,
    val answer: String,
    val guildId: Long,
    val queuedAt: Long
) {
    companion object {
        fun createId(guildId: Long, userId: Long): String = "$guildId:$userId"
    }
}
