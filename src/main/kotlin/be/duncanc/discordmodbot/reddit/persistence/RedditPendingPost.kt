package be.duncanc.discordmodbot.reddit.persistence

import org.springframework.data.annotation.Id
import org.springframework.data.redis.core.RedisHash
import org.springframework.data.redis.core.index.Indexed

@RedisHash("redditPendingPost", timeToLive = 900)
data class RedditPendingPost(
    @Id
    val id: String,
    @Indexed
    val guildId: Long
) {
    companion object {
        fun id(guildId: Long, redditPostId: String): String = "$guildId:$redditPostId"
    }
}
