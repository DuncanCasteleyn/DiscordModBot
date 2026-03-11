package be.duncanc.discordmodbot.logging.persistence


import org.springframework.data.annotation.Id
import org.springframework.data.redis.core.RedisHash

@RedisHash("attachmentProxy", timeToLive = 86400L)
data class AttachmentProxy(
    @Id
    val messageId: Long,
    val attachmentUrls: List<String> = emptyList(),
    val hadFailedCaches: Boolean = false
)
