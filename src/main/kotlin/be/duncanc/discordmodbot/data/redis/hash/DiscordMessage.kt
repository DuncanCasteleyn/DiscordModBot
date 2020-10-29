package be.duncanc.discordmodbot.data.redis.hash

import org.springframework.data.annotation.Id
import org.springframework.data.annotation.Transient
import org.springframework.data.redis.core.RedisHash

@RedisHash("discordMessage", timeToLive = 7200L)
data class DiscordMessage(
        @Id
        val messageId: Long,
        val guildId: Long,
        val channelId: Long,
        val userId: Long,
        val content: String,
        val emotes: String? = null
) {
        @Transient
        val jumpUrl = "https://discord.com/channels/$guildId/$channelId/$messageId"
}
