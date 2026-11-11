package be.duncanc.discordmodbot.member.gate.persistence

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.test.context.TestConstructor
import java.time.Duration
import java.time.Instant

@DataJpaTest
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class MemberGateAttemptRepositoryTest(
    private val memberGateAttemptRepository: MemberGateAttemptRepository
) {
    @Test
    fun `deleting stale attempts removes only records older than the cutoff`() {
        memberGateAttemptRepository.save(
            MemberGateAttempt(userId = 1L, attemptCount = 3, lastModified = Instant.now().minus(Duration.ofDays(100)))
        )
        memberGateAttemptRepository.save(
            MemberGateAttempt(userId = 2L, attemptCount = 1, lastModified = Instant.now().minus(Duration.ofDays(89)))
        )
        memberGateAttemptRepository.flush()

        val deletedCount = memberGateAttemptRepository.deleteByLastModifiedBefore(
            Instant.now().minus(Duration.ofDays(90))
        )

        assertEquals(1L, deletedCount)
        assertFalse(memberGateAttemptRepository.existsById(1L))
        assertTrue(memberGateAttemptRepository.existsById(2L))
    }
}
