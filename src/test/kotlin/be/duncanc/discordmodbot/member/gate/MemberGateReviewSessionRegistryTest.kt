package be.duncanc.discordmodbot.member.gate

import be.duncanc.discordmodbot.member.gate.persistence.MemberGateReviewSessionState
import be.duncanc.discordmodbot.member.gate.persistence.MemberGateReviewSessionStateRepository
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
class MemberGateReviewSessionRegistryTest {
    @Mock
    private lateinit var reviewSessionStateRepository: MemberGateReviewSessionStateRepository

    private lateinit var reviewSessionRegistry: MemberGateReviewSessionRegistry

    @BeforeEach
    fun setUp() {
        reviewSessionRegistry = MemberGateReviewSessionRegistry(reviewSessionStateRepository)
    }

    @Test
    fun `remember stores session state in redis repository`() {
        val session = MemberGateReviewSession(listOf(10L, 20L))
        session.skipCurrent()

        reviewSessionRegistry.remember(1L, 99L, session)

        val captor = argumentCaptor<MemberGateReviewSessionState>()
        verify(reviewSessionStateRepository).save(captor.capture())
        val state = captor.firstValue
        assertEquals("1:99", state.id)
        assertEquals(10L, state.oldestPendingUserId)
        assertEquals(listOf(20L, 10L), state.pendingUserIds)
    }

    @Test
    fun `get recreates session with original oldest applicant`() {
        whenever(reviewSessionStateRepository.findById("1:99")).thenReturn(
            Optional.of(
                MemberGateReviewSessionState(
                    id = "1:99",
                    guildId = 1L,
                    reviewerId = 99L,
                    oldestPendingUserId = 10L,
                    pendingUserIds = listOf(20L, 10L)
                )
            )
        )

        val session = reviewSessionRegistry.get(1L, 99L)

        assertEquals(20L, session?.getCurrentUserId())
        assertEquals(false, session?.isCurrentOldest())
        assertEquals(listOf(20L, 10L), session?.toPendingUserIds())
    }

    @Test
    fun `remember deletes completed sessions`() {
        val session = MemberGateReviewSession(listOf(10L))
        session.advanceAfterReview()

        reviewSessionRegistry.remember(1L, 99L, session)

        verify(reviewSessionStateRepository).deleteById("1:99")
    }

    @Test
    fun `get returns null when redis session is missing`() {
        whenever(reviewSessionStateRepository.findById("1:99")).thenReturn(Optional.empty())

        assertNull(reviewSessionRegistry.get(1L, 99L))
    }
}
