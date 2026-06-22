package be.duncanc.discordmodbot.moderation

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.ValueOperations
import java.time.Duration

@ExtendWith(MockitoExtension::class)
class ReportRateLimitServiceTest {
    @Mock
    private lateinit var redisTemplate: StringRedisTemplate

    @Mock
    private lateinit var valueOperations: ValueOperations<String, String>

    @Test
    fun `first report is allowed and stores per guild user key`() {
        val service = service(Duration.ofMinutes(5))
        whenever(redisTemplate.opsForValue()).thenReturn(valueOperations)
        whenever(valueOperations.setIfAbsent("report:rate-limit:1:99", "1", Duration.ofMinutes(5))).thenReturn(true)

        val allowed = service.tryConsume(1L, 99L)

        assertTrue(allowed)
        verify(valueOperations).setIfAbsent("report:rate-limit:1:99", "1", Duration.ofMinutes(5))
    }

    @Test
    fun `second report while key exists is rejected`() {
        val service = service(Duration.ofMinutes(5))
        whenever(redisTemplate.opsForValue()).thenReturn(valueOperations)
        whenever(valueOperations.setIfAbsent("report:rate-limit:1:99", "1", Duration.ofMinutes(5))).thenReturn(false)

        val allowed = service.tryConsume(1L, 99L)

        assertFalse(allowed)
    }

    @Test
    fun `zero duration disables rate limiting`() {
        val service = service(Duration.ZERO)

        val allowed = service.tryConsume(1L, 99L)

        assertTrue(allowed)
        verify(redisTemplate, never()).opsForValue()
    }

    @Test
    fun `negative duration disables rate limiting`() {
        val service = service(Duration.ofSeconds(-1))

        val allowed = service.tryConsume(1L, 99L)

        assertTrue(allowed)
        verify(redisTemplate, never()).opsForValue()
    }

    @Test
    fun `description formats default minutes`() {
        val service = service(Duration.ofMinutes(5))

        assertEquals("5 minutes", service.rateLimitDescription())
    }

    @Test
    fun `description formats seconds`() {
        val service = service(Duration.ofSeconds(30))

        assertEquals("30 seconds", service.rateLimitDescription())
    }

    @Test
    fun `description formats mixed hours and minutes`() {
        val service = service(Duration.ofMinutes(90))

        assertEquals("1 hour 30 minutes", service.rateLimitDescription())
    }

    @Test
    fun `description formats mixed days hours minutes and seconds`() {
        val service = service(Duration.ofSeconds(90061))

        assertEquals("1 day 1 hour 1 minute 1 second", service.rateLimitDescription())
    }

    @Test
    fun `key uses guild and user id`() {
        val service = service(Duration.ofMinutes(5))

        assertEquals("report:rate-limit:1:99", service.createKey(1L, 99L))
        assertEquals("report:rate-limit:2:99", service.createKey(2L, 99L))
    }

    @Test
    fun `hasActiveToken returns true when key exists`() {
        val service = service(Duration.ofMinutes(5))
        whenever(redisTemplate.hasKey("report:rate-limit:1:99")).thenReturn(true)

        val active = service.hasActiveToken(1L, 99L)

        assertTrue(active)
    }

    @Test
    fun `hasActiveToken returns false when key does not exist`() {
        val service = service(Duration.ofMinutes(5))
        whenever(redisTemplate.hasKey("report:rate-limit:1:99")).thenReturn(false)

        val active = service.hasActiveToken(1L, 99L)

        assertFalse(active)
    }

    @Test
    fun `hasActiveToken returns false when rate limiting is disabled`() {
        val service = service(Duration.ZERO)

        val active = service.hasActiveToken(1L, 99L)

        assertFalse(active)
        verify(redisTemplate, never()).hasKey(any<String>())
    }

    private fun service(rateLimit: Duration): ReportRateLimitService {
        return ReportRateLimitService(redisTemplate, ReportProperties(reportRateLimit = rateLimit))
    }
}
