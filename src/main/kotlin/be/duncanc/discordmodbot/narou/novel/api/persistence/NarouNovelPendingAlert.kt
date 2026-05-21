package be.duncanc.discordmodbot.narou.novel.api.persistence

import org.springframework.data.annotation.Id
import org.springframework.data.redis.core.RedisHash

@RedisHash("narouNovelPendingAlert", timeToLive = 900)
data class NarouNovelPendingAlert(
    @Id
    val guildId: Long,
    val snapshotLength: Long,
    val snapshotGeneralAllNo: Int,
    val snapshotNovelUpdatedAt: String
)
