package be.duncanc.discordmodbot.moderation

import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Service

@Service
class ReportedMessageService(
    private val redisTemplate: StringRedisTemplate,
    private val reportProperties: ReportProperties
) {
    fun getState(guildId: Long, channelId: Long, messageId: Long): ReportedMessageState? {
        val value = redisTemplate.opsForValue().get(createKey(guildId, channelId, messageId)) ?: return null
        return runCatching { ReportedMessageState.valueOf(value) }.getOrNull()
    }

    fun markReported(guildId: Long, channelId: Long, messageId: Long, urgent: Boolean) {
        markState(
            guildId = guildId,
            channelId = channelId,
            messageId = messageId,
            state = if (urgent) ReportedMessageState.URGENT else ReportedMessageState.NON_URGENT
        )
    }

    fun markUrgent(guildId: Long, channelId: Long, messageId: Long) {
        markState(guildId, channelId, messageId, ReportedMessageState.URGENT)
    }

    internal fun createKey(guildId: Long, channelId: Long, messageId: Long): String {
        return "report:message:$guildId:$channelId:$messageId"
    }

    private fun markState(guildId: Long, channelId: Long, messageId: Long, state: ReportedMessageState) {
        val retention = reportProperties.reportedMessageRetention
        if (!retention.isPositive) {
            return
        }

        redisTemplate.opsForValue().set(createKey(guildId, channelId, messageId), state.name, retention)
    }
}
