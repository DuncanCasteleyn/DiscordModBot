package be.duncanc.discordmodbot.data.redis.hash

import org.springframework.data.annotation.Id
import org.springframework.data.redis.core.RedisHash

@RedisHash("memberGateQuestion", timeToLive = 172800)
data class MemberGateQuestion(
    @Id
    val id: Long,
    val question: String,
    val answer: String
)
