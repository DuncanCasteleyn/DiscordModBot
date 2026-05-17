package be.duncanc.discordmodbot.logging.persistence

import org.springframework.data.annotation.Id
import org.springframework.data.redis.core.RedisHash

@RedisHash("messageDeleteAuditState", timeToLive = LOGGING_REDIS_TTL_SECONDS)
data class MessageDeleteAuditStateEntry(
    @Id
    val id: String,
    val guildId: Long,
    val channelId: Long,
    val targetUserId: Long,
    val consumedCounts: Map<Long, Int>
) {
    companion object {
        fun createId(guildId: Long, channelId: Long, targetUserId: Long): String = "$guildId:$channelId:$targetUserId"
    }
}
