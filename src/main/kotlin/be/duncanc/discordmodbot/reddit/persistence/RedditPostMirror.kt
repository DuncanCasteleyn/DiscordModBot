package be.duncanc.discordmodbot.reddit.persistence

import org.springframework.data.annotation.Id
import org.springframework.data.redis.core.RedisHash
import java.time.Instant

@RedisHash("redditPostMirror", timeToLive = 86400)
data class RedditPostMirror(
    @Id
    val id: String,
    val guildId: Long,
    val redditPostId: String,
    val discordChannelId: Long?,
    val discordMessageId: Long?,
    val publishedAt: Instant,
    val permalink: String,
    var deleted: Boolean = false
) {
    companion object {
        fun id(guildId: Long, redditPostId: String): String = "$guildId:$redditPostId"
    }
}
