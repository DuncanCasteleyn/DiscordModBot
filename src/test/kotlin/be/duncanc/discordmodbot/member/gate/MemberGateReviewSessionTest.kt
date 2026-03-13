package be.duncanc.discordmodbot.member.gate

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class MemberGateReviewSessionTest {
    @Test
    fun `approving applicants continues until queue is empty`() {
        val session = MemberGateReviewSession(listOf(10L, 20L, 30L))

        assertEquals(10L, session.getCurrentUserId())
        assertEquals(20L, session.advanceAfterReview())
        assertEquals(30L, session.advanceAfterReview())
        assertNull(session.advanceAfterReview())
        assertNull(session.getCurrentUserId())
    }

    @Test
    fun `approving the oldest applicant continues with the next applicant`() {
        val session = MemberGateReviewSession(listOf(10L, 20L, 30L))

        assertEquals(20L, session.advanceAfterReview())
        assertEquals(30L, session.advanceAfterReview())
        assertNull(session.advanceAfterReview())
        assertNull(session.getCurrentUserId())
    }

    @Test
    fun `resolving current applicant elsewhere continues with the next applicant`() {
        val session = MemberGateReviewSession(listOf(10L, 20L))

        assertEquals(20L, session.advancePastResolvedCurrent())
        assertNull(session.advancePastResolvedCurrent())
        assertNull(session.getCurrentUserId())
    }
}
