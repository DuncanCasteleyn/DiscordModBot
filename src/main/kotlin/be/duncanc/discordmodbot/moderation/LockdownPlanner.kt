package be.duncanc.discordmodbot.moderation

import be.duncanc.discordmodbot.moderation.persistence.LockdownTargetType
import net.dv8tion.jda.api.Permission

/**
 * Pure planning logic for channel lockdowns: decides which permission overrides change, without touching Discord.
 */
internal object LockdownPlanner {
    val LOCKED_PERMISSIONS: List<Permission> = listOf(
        Permission.MESSAGE_SEND,
        Permission.MESSAGE_SEND_IN_THREADS,
        Permission.CREATE_PUBLIC_THREADS,
        Permission.CREATE_PRIVATE_THREADS,
        Permission.MESSAGE_ADD_REACTION
    )
    val LOCKED_MASK: Long = Permission.getRaw(LOCKED_PERMISSIONS)

    /**
     * An existing channel override. [exempt] marks targets with Manage Channels or Administrator that must not be
     * locked.
     */
    data class OverrideSnapshot(
        val targetId: Long,
        val targetType: LockdownTargetType,
        val allow: Long,
        val deny: Long,
        val exempt: Boolean
    )

    data class OverrideEdit(
        val targetId: Long,
        val targetType: LockdownTargetType,
        val previousAllow: Long,
        val previousDeny: Long,
        val newAllow: Long,
        val newDeny: Long,
        val existed: Boolean
    )

    /** The state of an override as it was before the lockdown, as stored in the database. */
    data class SavedState(
        val targetId: Long,
        val targetType: LockdownTargetType,
        val previousAllow: Long,
        val previousDeny: Long,
        val existed: Boolean
    )

    sealed interface Restore {
        data object NoChange : Restore
        data object Delete : Restore
        data class Update(val allow: Long, val deny: Long) : Restore
    }

    /**
     * The overrides to change when locking. [protect] is applied first and gives the bot an explicit allow for the
     * locked permissions it holds: Discord only lets the bot change permission bits it holds in the channel, so
     * without it the bot would lose the ability to edit (and later restore) the channel once everyone is denied.
     * [lockEdits] starts with the everyone role.
     */
    data class LockPlan(val protect: OverrideEdit?, val lockEdits: List<OverrideEdit>) {
        val edits: List<OverrideEdit> get() = listOfNotNull(protect) + lockEdits
    }

    fun planLock(
        everyoneRoleId: Long,
        overrides: List<OverrideSnapshot>,
        selfMemberId: Long = 0L,
        selfPermissions: Long = 0L
    ): LockPlan {
        fun OverrideSnapshot.isSelf() = targetType == LockdownTargetType.MEMBER && targetId == selfMemberId

        val existingEveryone = overrides.firstOrNull {
            it.targetType == LockdownTargetType.ROLE && it.targetId == everyoneRoleId
        }
        val everyone = existingEveryone
            ?: OverrideSnapshot(everyoneRoleId, LockdownTargetType.ROLE, 0L, 0L, exempt = false)

        val others = overrides.filter {
            it != existingEveryone && !it.isSelf() && !it.exempt && it.allow and LOCKED_MASK != 0L
        }

        val protect = planProtect(selfMemberId, selfPermissions, overrides.firstOrNull { it.isSelf() })
        val lockEdits = listOf(lockEdit(everyone, existingEveryone != null)) + others.map { lockEdit(it, true) }
        return LockPlan(protect, lockEdits)
    }

    private fun planProtect(selfMemberId: Long, selfPermissions: Long, existing: OverrideSnapshot?): OverrideEdit? {
        val held = selfPermissions and LOCKED_MASK
        if (held == 0L || (existing != null && existing.allow and held == held)) {
            return null
        }

        val previousAllow = existing?.allow ?: 0L
        val previousDeny = existing?.deny ?: 0L
        return OverrideEdit(
            targetId = selfMemberId,
            targetType = LockdownTargetType.MEMBER,
            previousAllow = previousAllow,
            previousDeny = previousDeny,
            newAllow = previousAllow or held,
            newDeny = previousDeny and held.inv(),
            existed = existing != null
        )
    }

    fun planUnlock(saved: SavedState, current: OverrideSnapshot?): Restore {
        val savedAllow = saved.previousAllow and LOCKED_MASK
        val savedDeny = saved.previousDeny and LOCKED_MASK

        if (current == null) {
            return if (saved.existed && (savedAllow != 0L || savedDeny != 0L)) {
                Restore.Update(savedAllow, savedDeny)
            } else {
                Restore.NoChange
            }
        }

        val allow = (current.allow and LOCKED_MASK.inv()) or savedAllow
        val deny = (current.deny and LOCKED_MASK.inv()) or savedDeny
        return if (!saved.existed && allow == 0L && deny == 0L) Restore.Delete else Restore.Update(allow, deny)
    }

    private fun lockEdit(snapshot: OverrideSnapshot, existed: Boolean) = OverrideEdit(
        targetId = snapshot.targetId,
        targetType = snapshot.targetType,
        previousAllow = snapshot.allow,
        previousDeny = snapshot.deny,
        newAllow = snapshot.allow and LOCKED_MASK.inv(),
        newDeny = snapshot.deny or LOCKED_MASK,
        existed = existed
    )
}
