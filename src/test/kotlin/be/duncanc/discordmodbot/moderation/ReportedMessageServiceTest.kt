package be.duncanc.discordmodbot.moderation

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.ValueOperations
import java.time.Duration

@ExtendWith(MockitoExtension::class)
class ReportedMessageServiceTest {
    @Mock
    private lateinit var redisTemplate: StringRedisTemplate

    @Mock
    private lateinit var valueOperations: ValueOperations<String, String>

    @Test
    fun `missing state returns null`() {
        val service = service(Duration.ofHours(1))
        whenever(redisTemplate.opsForValue()).thenReturn(valueOperations)
        whenever(valueOperations.get("report:message:1:2:3")).thenReturn(null)

        val state = service.getState(1L, 2L, 3L)

        assertNull(state)
    }

    @Test
    fun `stored non urgent state is returned`() {
        val service = service(Duration.ofHours(1))
        whenever(redisTemplate.opsForValue()).thenReturn(valueOperations)
        whenever(valueOperations.get("report:message:1:2:3")).thenReturn("NON_URGENT")

        val state = service.getState(1L, 2L, 3L)

        assertEquals(ReportedMessageState.NON_URGENT, state)
    }

    @Test
    fun `invalid state returns null`() {
        val service = service(Duration.ofHours(1))
        whenever(redisTemplate.opsForValue()).thenReturn(valueOperations)
        whenever(valueOperations.get("report:message:1:2:3")).thenReturn("UNKNOWN")

        val state = service.getState(1L, 2L, 3L)

        assertNull(state)
    }

    @Test
    fun `mark reported stores non urgent state with retention`() {
        val service = service(Duration.ofHours(1))
        whenever(redisTemplate.opsForValue()).thenReturn(valueOperations)

        service.markReported(1L, 2L, 3L, urgent = false)

        verify(valueOperations).set("report:message:1:2:3", "NON_URGENT", Duration.ofHours(1))
    }

    @Test
    fun `mark urgent stores urgent state with retention`() {
        val service = service(Duration.ofHours(1))
        whenever(redisTemplate.opsForValue()).thenReturn(valueOperations)

        service.markUrgent(1L, 2L, 3L)

        verify(valueOperations).set("report:message:1:2:3", "URGENT", Duration.ofHours(1))
    }

    @Test
    fun `non-positive retention disables tracking writes`() {
        val service = service(Duration.ZERO)

        service.markReported(1L, 2L, 3L, urgent = false)

        verify(redisTemplate, never()).opsForValue()
    }

    @Test
    fun `key includes guild channel and message id`() {
        val service = service(Duration.ofHours(1))

        assertEquals("report:message:1:2:3", service.createKey(1L, 2L, 3L))
        assertEquals("report:message:1:4:3", service.createKey(1L, 4L, 3L))
    }

    private fun service(retention: Duration): ReportedMessageService {
        return ReportedMessageService(redisTemplate, ReportProperties(reportedMessageRetention = retention))
    }
}
