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
        if (rateLimit.toHoursPart() > 0 || rateLimit.toDaysPart() > 0) {
            val hours = rateLimit.toHours()
            return "$hours ${if (hours == 1L) "hour" else "hours"}"
        }

        if (rateLimit.toMinutesPart() > 0) {
            val minutes = rateLimit.toMinutes()
            return "$minutes ${if (minutes == 1L) "minute" else "minutes"}"
        }

        val seconds = rateLimit.seconds.coerceAtLeast(1)
        return "$seconds ${if (seconds == 1L) "second" else "seconds"}"
    }

    internal fun createKey(guildId: Long, userId: Long): String {
        return "report:rate-limit:$guildId:$userId"
    }

    private val java.time.Duration.isPositive: Boolean
        get() = !isZero && !isNegative

    companion object {
        private const val RATE_LIMIT_VALUE = "1"
    }
}
