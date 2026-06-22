package be.duncanc.discordmodbot.moderation

import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Service

@Service
class ReportRateLimitService(
    private val redisTemplate: StringRedisTemplate,
    private val reportProperties: ReportProperties
) {
    fun tryConsume(guildId: Long, userId: Long): Boolean {
        val rateLimit = reportProperties.reportRateLimit
        if (!rateLimit.isPositive) {
            return true
        }

        return redisTemplate.opsForValue()
            .setIfAbsent(createKey(guildId, userId), RATE_LIMIT_VALUE, rateLimit) == true
    }

    fun rateLimitDescription(): String {
        val rateLimit = reportProperties.reportRateLimit
        val days = rateLimit.toDaysPart().toInt()
        val hours = rateLimit.toHoursPart()
        val minutes = rateLimit.toMinutesPart()
        val seconds = rateLimit.toSecondsPart()

        val parts = buildList {
            if (days > 0) add("$days ${if (days == 1) "day" else "days"}")
            if (hours > 0) add("$hours ${if (hours == 1) "hour" else "hours"}")
            if (minutes > 0) add("$minutes ${if (minutes == 1) "minute" else "minutes"}")
            if (seconds > 0) add("$seconds ${if (seconds == 1) "second" else "seconds"}")
        }

        return parts.joinToString(" ").ifEmpty { "0 seconds" }
    }

    internal fun createKey(guildId: Long, userId: Long): String {
        return "report:rate-limit:$guildId:$userId"
    }

    companion object {
        private const val RATE_LIMIT_VALUE = "1"
    }
}
