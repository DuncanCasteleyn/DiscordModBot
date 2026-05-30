package be.duncanc.discordmodbot.member.gate

import be.duncanc.discordmodbot.member.gate.persistence.ReviewSessionState
import be.duncanc.discordmodbot.member.gate.persistence.ReviewSessionStateRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.*

@ExtendWith(MockitoExtension::class)
class ReviewSessionRegistryTest {
    @Mock
    private lateinit var reviewSessionStateRepository: ReviewSessionStateRepository

    private lateinit var reviewSessionRegistry: ReviewSessionRegistry

    @BeforeEach
    fun setUp() {
        reviewSessionRegistry = ReviewSessionRegistry(reviewSessionStateRepository)
    }

    @Test
    fun `remember stores session state in redis repository`() {
        val session = ReviewSession(
            pendingUserIds = listOf(20L, 10L),
            oldestPendingUserId = 10L,
            approvedCount = 1,
            rejectedCount = 2,
            manualActionCount = 3
        )

        reviewSessionRegistry.remember(1L, 99L, session)

        val captor = argumentCaptor<ReviewSessionState>()
        verify(reviewSessionStateRepository).save(captor.capture())
        val state = captor.firstValue
        assertEquals("1:99", state.id)
        assertEquals(10L, state.oldestPendingUserId)
        assertEquals(listOf(20L, 10L), state.pendingUserIds)
        assertEquals(1, state.approvedCount)
        assertEquals(2, state.rejectedCount)
        assertEquals(3, state.manualActionCount)
    }

    @Test
    fun `get recreates session with original oldest applicant`() {
        whenever(reviewSessionStateRepository.findById("1:99")).thenReturn(
            Optional.of(
                ReviewSessionState(
                    id = "1:99",
                    guildId = 1L,
                    reviewerId = 99L,
                    oldestPendingUserId = 10L,
                    pendingUserIds = listOf(20L, 10L),
                    approvedCount = 1,
                    rejectedCount = 2,
                    manualActionCount = 3
                )
            )
        )

        val session = reviewSessionRegistry.get(1L, 99L)

        assertEquals(20L, session?.getCurrentUserId())
        assertEquals(false, session?.isCurrentOldest())
        assertEquals(listOf(20L, 10L), session?.toPendingUserIds())
        assertEquals(1, session?.approvedCount)
        assertEquals(2, session?.rejectedCount)
        assertEquals(3, session?.manualActionCount)
    }

    @Test
    fun `remember deletes completed sessions`() {
        val session = ReviewSession(listOf(10L))
        session.advanceAfterReview()

        reviewSessionRegistry.remember(1L, 99L, session)

        verify(reviewSessionStateRepository).deleteById("1:99")
    }

    @Test
    fun `get returns null when redis session is missing`() {
        whenever(reviewSessionStateRepository.findById("1:99")).thenReturn(Optional.empty())

        assertNull(reviewSessionRegistry.get(1L, 99L))
    }

    @Test
    fun `forget other sessions returns and deletes sessions from other reviewers in guild`() {
        whenever(reviewSessionStateRepository.findAll()).thenReturn(
            listOf(
                ReviewSessionState(
                    id = "1:42",
                    guildId = 1L,
                    reviewerId = 42L,
                    oldestPendingUserId = 10L,
                    pendingUserIds = listOf(10L),
                    approvedCount = 1,
                    rejectedCount = 2,
                    manualActionCount = 3
                ),
                ReviewSessionState(
                    id = "1:99",
                    guildId = 1L,
                    reviewerId = 99L,
                    oldestPendingUserId = 20L,
                    pendingUserIds = listOf(20L)
                ),
                ReviewSessionState(
                    id = "2:43",
                    guildId = 2L,
                    reviewerId = 43L,
                    oldestPendingUserId = 30L,
                    pendingUserIds = listOf(30L)
                )
            )
        )

        val sessions = reviewSessionRegistry.forgetOtherSessions(1L, 99L)

        assertEquals(1, sessions.size)
        assertEquals(42L, sessions.first().reviewerId)
        assertEquals(10L, sessions.first().session.getCurrentUserId())
        assertEquals(1, sessions.first().session.approvedCount)
        assertEquals(2, sessions.first().session.rejectedCount)
        assertEquals(3, sessions.first().session.manualActionCount)
        verify(reviewSessionStateRepository).deleteById("1:42")
    }
}
