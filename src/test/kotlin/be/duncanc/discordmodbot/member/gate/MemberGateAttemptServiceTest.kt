package be.duncanc.discordmodbot.member.gate

import be.duncanc.discordmodbot.member.gate.persistence.MemberGateAttempt
import be.duncanc.discordmodbot.member.gate.persistence.MemberGateAttemptRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.Duration
import java.time.Instant
import java.util.Optional

@ExtendWith(MockitoExtension::class)
class MemberGateAttemptServiceTest {
    @Mock
    private lateinit var memberGateAttemptRepository: MemberGateAttemptRepository

    private lateinit var service: MemberGateAttemptService

    @BeforeEach
    fun setUp() {
        service = MemberGateAttemptService(memberGateAttemptRepository)
    }

    @Test
    fun `recording an attempt creates a record with count one for a new user`() {
        whenever(memberGateAttemptRepository.findById(10L)).thenReturn(Optional.empty())

        service.recordAttempt(10L)

        val savedAttempt = argumentCaptor<MemberGateAttempt>()
        verify(memberGateAttemptRepository).save(savedAttempt.capture())
        assertEquals(10L, savedAttempt.firstValue.userId)
        assertEquals(1, savedAttempt.firstValue.attemptCount)
    }

    @Test
    fun `recording an attempt increments the count and refreshes the timestamp`() {
        val existingAttempt = MemberGateAttempt(
            userId = 10L,
            attemptCount = 2,
            lastModified = Instant.now().minusSeconds(3600)
        )
        whenever(memberGateAttemptRepository.findById(10L)).thenReturn(Optional.of(existingAttempt))
        val beforeRecording = Instant.now()

        service.recordAttempt(10L)

        val savedAttempt = argumentCaptor<MemberGateAttempt>()
        verify(memberGateAttemptRepository).save(savedAttempt.capture())
        assertEquals(3, savedAttempt.firstValue.attemptCount)
        assertTrue(!savedAttempt.firstValue.lastModified.isBefore(beforeRecording))
    }

    @Test
    fun `clearing attempts deletes the record for the user`() {
        service.clearAttempts(10L)

        verify(memberGateAttemptRepository).deleteById(10L)
    }

    @Test
    fun `getting the attempt count returns the stored count`() {
        whenever(memberGateAttemptRepository.findById(10L)).thenReturn(
            Optional.of(MemberGateAttempt(userId = 10L, attemptCount = 4, lastModified = Instant.now()))
        )

        assertEquals(4, service.getAttemptCount(10L))
    }

    @Test
    fun `getting the attempt count returns null when no record exists`() {
        whenever(memberGateAttemptRepository.findById(10L)).thenReturn(Optional.empty())

        assertNull(service.getAttemptCount(10L))
    }

    @Test
    fun `stale attempt cleanup deletes records not modified for more than 90 days`() {
        val beforeCleanup = Instant.now()

        service.deleteStaleAttempts()

        val afterCleanup = Instant.now()
        val cutoffCaptor = argumentCaptor<Instant>()
        verify(memberGateAttemptRepository).deleteByLastModifiedBefore(cutoffCaptor.capture())
        val cutoff = cutoffCaptor.firstValue
        val expectedEarliest = beforeCleanup.minus(Duration.ofDays(90))
        val expectedLatest = afterCleanup.minus(Duration.ofDays(90))
        assertTrue(!cutoff.isBefore(expectedEarliest))
        assertTrue(!cutoff.isAfter(expectedLatest))
    }
}
