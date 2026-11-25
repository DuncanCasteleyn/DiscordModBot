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

    fun planLock(everyoneRoleId: Long, overrides: List<OverrideSnapshot>): List<OverrideEdit> {
        val existingEveryone = overrides.firstOrNull {
            it.targetType == LockdownTargetType.ROLE && it.targetId == everyoneRoleId
        }
        val everyone = existingEveryone
            ?: OverrideSnapshot(everyoneRoleId, LockdownTargetType.ROLE, 0L, 0L, exempt = false)

        val others = overrides.filter {
            it != existingEveryone && !it.exempt && it.allow and LOCKED_MASK != 0L
        }

        return listOf(lockEdit(everyone, existingEveryone != null)) + others.map { lockEdit(it, true) }
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
