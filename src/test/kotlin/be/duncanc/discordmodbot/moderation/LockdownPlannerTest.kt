package be.duncanc.discordmodbot.moderation

import be.duncanc.discordmodbot.moderation.persistence.LockdownTargetType
import net.dv8tion.jda.api.Permission
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LockdownPlannerTest {
    companion object {
        private const val EVERYONE_ID = 1L
        private const val ROLE_ID = 2L
        private const val MEMBER_ID = 3L
        private const val SELF_ID = 4L

        private val LOCKED_PERMISSIONS = listOf(
            Permission.MESSAGE_SEND,
            Permission.MESSAGE_SEND_IN_THREADS,
            Permission.CREATE_PUBLIC_THREADS,
            Permission.CREATE_PRIVATE_THREADS,
            Permission.MESSAGE_ADD_REACTION
        )
        private val MASK = Permission.getRaw(LOCKED_PERMISSIONS)
        private val OTHER = Permission.getRaw(Permission.VIEW_CHANNEL)
    }

    @Test
    fun `locking denies all five permissions for everyone even without an existing override`() {
        val edits = LockdownPlanner.planLock(EVERYONE_ID, emptyList()).edits

        assertEquals(1, edits.size)
        val edit = edits.single()
        assertEquals(EVERYONE_ID, edit.targetId)
        assertEquals(LockdownTargetType.ROLE, edit.targetType)
        assertEquals(0L, edit.newAllow)
        assertEquals(MASK, edit.newDeny)
        assertEquals(0L, edit.previousAllow)
        assertEquals(0L, edit.previousDeny)
    }

    @Test
    fun `locking keeps unrelated bits and remembers the previous state of everyone`() {
        val existing = LockdownPlanner.OverrideSnapshot(
            EVERYONE_ID, LockdownTargetType.ROLE,
            allow = OTHER or Permission.getRaw(Permission.MESSAGE_SEND),
            deny = Permission.getRaw(Permission.MESSAGE_ADD_REACTION),
            exempt = false
        )

        val edit = LockdownPlanner.planLock(EVERYONE_ID, listOf(existing)).edits.single()

        assertEquals(OTHER, edit.newAllow)
        assertEquals(MASK, edit.newDeny)
        assertEquals(existing.allow, edit.previousAllow)
        assertEquals(existing.deny, edit.previousDeny)
    }

    @Test
    fun `locking denies roles and members that have an explicit allow`() {
        val role = LockdownPlanner.OverrideSnapshot(
            ROLE_ID, LockdownTargetType.ROLE, Permission.getRaw(Permission.MESSAGE_SEND), 0L, exempt = false
        )
        val member = LockdownPlanner.OverrideSnapshot(
            MEMBER_ID, LockdownTargetType.MEMBER, Permission.getRaw(Permission.CREATE_PUBLIC_THREADS), 0L,
            exempt = false
        )

        val edits = LockdownPlanner.planLock(EVERYONE_ID, listOf(role, member)).edits

        assertEquals(listOf(EVERYONE_ID, ROLE_ID, MEMBER_ID), edits.map { it.targetId })
        assertEquals(LockdownTargetType.MEMBER, edits.last().targetType)
        assertTrue(edits.all { it.newDeny == MASK && it.newAllow and MASK == 0L })
    }

    @Test
    fun `unlocking restores only the locked bits and keeps changes made during the lockdown`() {
        val saved = LockdownPlanner.SavedState(
            EVERYONE_ID, LockdownTargetType.ROLE,
            previousAllow = Permission.getRaw(Permission.MESSAGE_SEND),
            previousDeny = Permission.getRaw(Permission.MESSAGE_ADD_REACTION),
            existed = true
        )
        val current = LockdownPlanner.OverrideSnapshot(
            EVERYONE_ID, LockdownTargetType.ROLE, allow = OTHER, deny = MASK, exempt = false
        )

        val restore = LockdownPlanner.planUnlock(saved, current) as LockdownPlanner.Restore.Update

        assertEquals(OTHER or Permission.getRaw(Permission.MESSAGE_SEND), restore.allow)
        assertEquals(Permission.getRaw(Permission.MESSAGE_ADD_REACTION), restore.deny)
    }

    @Test
    fun `unlocking deletes an override that did not exist before and is now empty`() {
        val saved = LockdownPlanner.SavedState(
            EVERYONE_ID, LockdownTargetType.ROLE, previousAllow = 0L, previousDeny = 0L, existed = false
        )
        val current = LockdownPlanner.OverrideSnapshot(
            EVERYONE_ID, LockdownTargetType.ROLE, allow = 0L, deny = MASK, exempt = false
        )

        assertEquals(LockdownPlanner.Restore.Delete, LockdownPlanner.planUnlock(saved, current))
    }

    @Test
    fun `unlocking keeps an override that was empty but existed before the lockdown`() {
        val saved = LockdownPlanner.SavedState(
            EVERYONE_ID, LockdownTargetType.ROLE, previousAllow = 0L, previousDeny = 0L, existed = true
        )
        val current = LockdownPlanner.OverrideSnapshot(
            EVERYONE_ID, LockdownTargetType.ROLE, allow = 0L, deny = MASK, exempt = false
        )

        assertEquals(LockdownPlanner.Restore.Update(0L, 0L), LockdownPlanner.planUnlock(saved, current))
    }

    @Test
    fun `unlocking does nothing when the override was removed manually and did not exist before`() {
        val saved = LockdownPlanner.SavedState(
            EVERYONE_ID, LockdownTargetType.ROLE, previousAllow = 0L, previousDeny = 0L, existed = false
        )

        assertEquals(LockdownPlanner.Restore.NoChange, LockdownPlanner.planUnlock(saved, null))
    }

    @Test
    fun `unlocking recreates an override that was removed manually but existed before`() {
        val saved = LockdownPlanner.SavedState(
            EVERYONE_ID, LockdownTargetType.ROLE,
            previousAllow = Permission.getRaw(Permission.MESSAGE_SEND), previousDeny = 0L, existed = true
        )

        val restore = LockdownPlanner.planUnlock(saved, null) as LockdownPlanner.Restore.Update

        assertEquals(Permission.getRaw(Permission.MESSAGE_SEND), restore.allow)
        assertEquals(0L, restore.deny)
    }

    @Test
    fun `locking first lets the bot keep the permissions it holds, so it can still unlock afterwards`() {
        val plan = LockdownPlanner.planLock(
            EVERYONE_ID, emptyList(), selfMemberId = SELF_ID,
            selfPermissions = Permission.getRaw(Permission.MESSAGE_SEND, Permission.VIEW_CHANNEL)
        )

        val protect = plan.protect!!
        assertEquals(listOf(SELF_ID, EVERYONE_ID), plan.edits.map { it.targetId })
        assertEquals(LockdownTargetType.MEMBER, protect.targetType)
        assertEquals(Permission.getRaw(Permission.MESSAGE_SEND), protect.newAllow)
        assertEquals(0L, protect.newDeny)
        assertEquals(false, protect.existed)
    }

    @Test
    fun `locking keeps the existing override of the bot and clears its denies for the locked permissions`() {
        val self = LockdownPlanner.OverrideSnapshot(
            SELF_ID, LockdownTargetType.MEMBER,
            allow = OTHER, deny = Permission.getRaw(Permission.MESSAGE_ADD_REACTION), exempt = true
        )

        val plan = LockdownPlanner.planLock(
            EVERYONE_ID, listOf(self), selfMemberId = SELF_ID, selfPermissions = MASK
        )

        val protect = plan.protect!!
        assertEquals(OTHER or MASK, protect.newAllow)
        assertEquals(0L, protect.newDeny)
        assertEquals(self.allow, protect.previousAllow)
        assertEquals(self.deny, protect.previousDeny)
        assertEquals(true, protect.existed)
        assertEquals(listOf(SELF_ID, EVERYONE_ID), plan.edits.map { it.targetId })
    }

    @Test
    fun `locking does not touch the bot when it already has an explicit allow or holds none of the permissions`() {
        val self = LockdownPlanner.OverrideSnapshot(SELF_ID, LockdownTargetType.MEMBER, MASK, 0L, exempt = false)

        val alreadyAllowed = LockdownPlanner.planLock(
            EVERYONE_ID, listOf(self), selfMemberId = SELF_ID, selfPermissions = MASK
        )
        val holdsNothing = LockdownPlanner.planLock(
            EVERYONE_ID, emptyList(), selfMemberId = SELF_ID, selfPermissions = OTHER
        )

        assertEquals(null, alreadyAllowed.protect)
        assertEquals(listOf(EVERYONE_ID), alreadyAllowed.edits.map { it.targetId })
        assertEquals(null, holdsNothing.protect)
    }

    @Test
    fun `locking leaves exempt targets and targets without explicit allows alone`() {
        val exemptRole = LockdownPlanner.OverrideSnapshot(
            ROLE_ID, LockdownTargetType.ROLE, Permission.getRaw(Permission.MESSAGE_SEND), 0L, exempt = true
        )
        val denyOnlyMember = LockdownPlanner.OverrideSnapshot(
            MEMBER_ID, LockdownTargetType.MEMBER, OTHER, Permission.getRaw(Permission.MESSAGE_SEND), exempt = false
        )

        val edits = LockdownPlanner.planLock(EVERYONE_ID, listOf(exemptRole, denyOnlyMember)).edits

        assertEquals(listOf(EVERYONE_ID), edits.map { it.targetId })
    }
}
