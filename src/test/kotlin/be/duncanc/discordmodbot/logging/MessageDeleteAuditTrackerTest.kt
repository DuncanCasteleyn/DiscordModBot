package be.duncanc.discordmodbot.logging

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MessageDeleteAuditTrackerTest {
    @Test
    fun `consume should match the first delete for a new audit log entry`() {
        val result = MessageDeleteAuditTracker.consume(previousState = null, entryId = 42L, count = 1)

        assertTrue(result.matched)
        assertEquals(MessageDeleteAuditState(entryId = 42L, count = 1, consumedCount = 1), result.nextState)
    }

    @Test
    fun `consume should match repeated deletes from the same audit log entry`() {
        val firstResult = MessageDeleteAuditTracker.consume(previousState = null, entryId = 42L, count = 2)
        val secondResult = MessageDeleteAuditTracker.consume(previousState = firstResult.nextState, entryId = 42L, count = 2)

        assertTrue(firstResult.matched)
        assertTrue(secondResult.matched)
        assertEquals(MessageDeleteAuditState(entryId = 42L, count = 2, consumedCount = 2), secondResult.nextState)
    }

    @Test
    fun `consume should use newly increased count on the same audit log entry`() {
        val firstResult = MessageDeleteAuditTracker.consume(previousState = null, entryId = 42L, count = 2)
        val secondResult = MessageDeleteAuditTracker.consume(previousState = firstResult.nextState, entryId = 42L, count = 3)
        val thirdResult = MessageDeleteAuditTracker.consume(previousState = secondResult.nextState, entryId = 42L, count = 3)

        assertTrue(firstResult.matched)
        assertTrue(secondResult.matched)
        assertTrue(thirdResult.matched)
        assertEquals(MessageDeleteAuditState(entryId = 42L, count = 3, consumedCount = 3), thirdResult.nextState)
    }

    @Test
    fun `consume should stop matching once the known count is exhausted`() {
        val firstResult = MessageDeleteAuditTracker.consume(previousState = null, entryId = 42L, count = 1)
        val secondResult = MessageDeleteAuditTracker.consume(previousState = firstResult.nextState, entryId = 42L, count = 1)

        assertTrue(firstResult.matched)
        assertFalse(secondResult.matched)
        assertEquals(MessageDeleteAuditState(entryId = 42L, count = 1, consumedCount = 1), secondResult.nextState)
    }

    @Test
    fun `consume should reset consumption for a newer audit log entry`() {
        val firstResult = MessageDeleteAuditTracker.consume(previousState = null, entryId = 42L, count = 2)
        val secondResult = MessageDeleteAuditTracker.consume(previousState = firstResult.nextState, entryId = 43L, count = 1)

        assertTrue(firstResult.matched)
        assertTrue(secondResult.matched)
        assertEquals(MessageDeleteAuditState(entryId = 43L, count = 1, consumedCount = 1), secondResult.nextState)
    }
}
