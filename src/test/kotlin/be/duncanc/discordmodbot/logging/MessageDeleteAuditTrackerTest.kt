package be.duncanc.discordmodbot.logging

import kotlin.test.Test
import kotlin.test.assertEquals

class MessageDeleteAuditTrackerTest {
    @Test
    fun `consume should match the first delete for a new audit log entry`() {
        val result = MessageDeleteAuditTracker.consume(previousState = null, candidates = listOf(candidate(42L, 1)))

        assertEquals(42L, result.matchedEntryId)
        assertEquals(MessageDeleteAuditState(consumedCounts = mapOf(42L to 1)), result.nextState)
    }

    @Test
    fun `consume should match repeated deletes from the same audit log entry`() {
        val candidates = listOf(candidate(42L, 2))
        val firstResult = MessageDeleteAuditTracker.consume(previousState = null, candidates = candidates)
        val secondResult = MessageDeleteAuditTracker.consume(previousState = firstResult.nextState, candidates = candidates)

        assertEquals(42L, firstResult.matchedEntryId)
        assertEquals(42L, secondResult.matchedEntryId)
        assertEquals(MessageDeleteAuditState(consumedCounts = mapOf(42L to 2)), secondResult.nextState)
    }

    @Test
    fun `consume should use newly increased count on the same audit log entry`() {
        val firstResult = MessageDeleteAuditTracker.consume(previousState = null, candidates = listOf(candidate(42L, 2)))
        val secondResult = MessageDeleteAuditTracker.consume(previousState = firstResult.nextState, candidates = listOf(candidate(42L, 3)))
        val thirdResult = MessageDeleteAuditTracker.consume(previousState = secondResult.nextState, candidates = listOf(candidate(42L, 3)))

        assertEquals(42L, firstResult.matchedEntryId)
        assertEquals(42L, secondResult.matchedEntryId)
        assertEquals(42L, thirdResult.matchedEntryId)
        assertEquals(MessageDeleteAuditState(consumedCounts = mapOf(42L to 3)), thirdResult.nextState)
    }

    @Test
    fun `consume should stop matching once the known count is exhausted`() {
        val candidates = listOf(candidate(42L, 1))
        val firstResult = MessageDeleteAuditTracker.consume(previousState = null, candidates = candidates)
        val secondResult = MessageDeleteAuditTracker.consume(previousState = firstResult.nextState, candidates = candidates)

        assertEquals(42L, firstResult.matchedEntryId)
        assertEquals(null, secondResult.matchedEntryId)
        assertEquals(MessageDeleteAuditState(consumedCounts = mapOf(42L to 1)), secondResult.nextState)
    }

    @Test
    fun `consume should reset consumption for a newer audit log entry`() {
        val firstResult = MessageDeleteAuditTracker.consume(previousState = null, candidates = listOf(candidate(42L, 2)))
        val secondResult = MessageDeleteAuditTracker.consume(previousState = firstResult.nextState, candidates = listOf(candidate(43L, 1)))

        assertEquals(42L, firstResult.matchedEntryId)
        assertEquals(43L, secondResult.matchedEntryId)
        assertEquals(MessageDeleteAuditState(consumedCounts = mapOf(43L to 1)), secondResult.nextState)
    }

    @Test
    fun `consume should prefer the oldest remaining audit log entry`() {
        val candidates = listOf(candidate(43L, 1), candidate(42L, 1))

        val firstResult = MessageDeleteAuditTracker.consume(previousState = null, candidates = candidates)
        val secondResult = MessageDeleteAuditTracker.consume(previousState = firstResult.nextState, candidates = candidates)

        assertEquals(42L, firstResult.matchedEntryId)
        assertEquals(43L, secondResult.matchedEntryId)
        assertEquals(MessageDeleteAuditState(consumedCounts = mapOf(42L to 1, 43L to 1)), secondResult.nextState)
    }

    @Test
    fun `consume should fall back to an older audit log entry when the newest is exhausted`() {
        val result = MessageDeleteAuditTracker.consume(
            previousState = MessageDeleteAuditState(consumedCounts = mapOf(43L to 1)),
            candidates = listOf(candidate(43L, 1), candidate(42L, 1))
        )

        assertEquals(42L, result.matchedEntryId)
        assertEquals(MessageDeleteAuditState(consumedCounts = mapOf(43L to 1, 42L to 1)), result.nextState)
    }

    @Test
    fun `consume should ignore historical entries at or before the watermark`() {
        val result = MessageDeleteAuditTracker.consume(
            previousState = null,
            candidates = listOf(candidate(43L, 1), candidate(42L, 1)),
            watermark = watermark(43L, 1)
        )

        assertEquals(null, result.matchedEntryId)
        assertEquals(MessageDeleteAuditState(consumedCounts = mapOf(43L to 1, 42L to 1)), result.nextState)
    }

    @Test
    fun `consume should still match newly increased count on the watermark entry`() {
        val result = MessageDeleteAuditTracker.consume(
            previousState = null,
            candidates = listOf(candidate(43L, 2)),
            watermark = watermark(43L, 1)
        )

        assertEquals(43L, result.matchedEntryId)
        assertEquals(MessageDeleteAuditState(consumedCounts = mapOf(43L to 2)), result.nextState)
    }

    private fun candidate(entryId: Long, count: Int): MessageDeleteAuditCandidate = MessageDeleteAuditCandidate(
        entryId = entryId,
        count = count
    )

    private fun watermark(entryId: Long, count: Int): MessageDeleteAuditWatermark = MessageDeleteAuditWatermark(
        entryId = entryId,
        count = count
    )
}
