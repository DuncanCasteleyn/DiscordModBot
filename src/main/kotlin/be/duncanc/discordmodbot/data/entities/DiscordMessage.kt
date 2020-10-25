package be.duncanc.discordmodbot.data.entities

import org.springframework.data.annotation.Id
import org.springframework.data.redis.core.RedisHash

@RedisHash("discordMessage", timeToLive = 7200L)
data class DiscordMessage(
        @Id
        val messageId: Long,
        val channelId: Long,
        val userId: Long,
        val content: String
)
