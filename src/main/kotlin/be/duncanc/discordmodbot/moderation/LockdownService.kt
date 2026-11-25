package be.duncanc.discordmodbot.moderation

import be.duncanc.discordmodbot.moderation.persistence.ChannelLockdown
import be.duncanc.discordmodbot.moderation.persistence.ChannelLockdownRepository
import be.duncanc.discordmodbot.moderation.persistence.LockdownTargetType
import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.entities.IPermissionHolder
import net.dv8tion.jda.api.entities.PermissionOverride
import net.dv8tion.jda.api.entities.channel.middleman.StandardGuildMessageChannel
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap

@Service
@Transactional(readOnly = true)
class LockdownService(
    private val channelLockdownRepository: ChannelLockdownRepository
) {
    companion object {
        private val LOG = LoggerFactory.getLogger(LockdownService::class.java)
    }

    // Channels that are being locked or unlocked right now, so the two can never interleave on one channel.
    private val channelsInProgress = ConcurrentHashMap.newKeySet<Pair<Long, Long>>()

    sealed interface LockResult {
        data object AlreadyLocked : LockResult
        data object InProgress : LockResult
        data class Locked(val couldNotLockRoleIds: List<Long>, val couldNotLockMemberIds: List<Long>) : LockResult
        data class Failed(val reason: String) : LockResult

        /** Everyone is locked, but applying the other overrides failed. The saved state is kept for unlocking. */
        data class PartiallyLocked(val reason: String) : LockResult
    }

    sealed interface UnlockResult {
        data object NotLocked : UnlockResult
        data object InProgress : UnlockResult
        data class Unlocked(val failedTargets: List<ChannelLockdown.ChannelLockdownId>) : UnlockResult
    }

    @Transactional
    fun lock(channel: StandardGuildMessageChannel): CompletableFuture<LockResult> {
        val guildId = channel.guild.idLong
        val channelId = channel.idLong
        val key = guildId to channelId

        // Guards against two moderators locking the same channel at once, which would save the already locked state.
        if (!channelsInProgress.add(key)) {
            return CompletableFuture.completedFuture(LockResult.InProgress)
        }

        try {
            if (channelLockdownRepository.existsByGuildIdAndChannelId(guildId, channelId)) {
                channelsInProgress.remove(key)
                return CompletableFuture.completedFuture(LockResult.AlreadyLocked)
            }

            val snapshots = channel.permissionOverrides.map { toSnapshot(it) }
            val edits = LockdownPlanner.planLock(channel.guild.publicRole.idLong, snapshots)

            channelLockdownRepository.saveAll(edits.map {
                ChannelLockdown(
                    guildId, channelId, it.targetType, it.targetId, it.previousAllow, it.previousDeny, it.existed
                )
            })

            return lockEveryoneThenOthers(channel, edits)
                .whenComplete { _, _ -> channelsInProgress.remove(key) }
        } catch (e: Exception) {
            channelsInProgress.remove(key)
            throw e
        }
    }

    private fun lockEveryoneThenOthers(
        channel: StandardGuildMessageChannel,
        edits: List<LockdownPlanner.OverrideEdit>
    ): CompletableFuture<LockResult> {
        val guildId = channel.guild.idLong
        val channelId = channel.idLong
        val everyoneEdit = edits.first()
        val otherEdits = edits.drop(1)

        return applyEdit(channel, everyoneEdit)
            .handle<Throwable?> { _, throwable -> throwable }
            .thenCompose<LockResult> { everyoneError ->
                if (everyoneError != null) {
                    // Nothing was changed in Discord, so there is nothing to restore later.
                    LOG.warn("Failed to lock channel {} in guild {}", channelId, guildId, everyoneError)
                    channelLockdownRepository.deleteAllByGuildIdAndChannelId(guildId, channelId)
                    CompletableFuture.completedFuture(LockResult.Failed(rootMessage(everyoneError)))
                } else {
                    lockOthers(channel, otherEdits).exceptionally { throwable ->
                        // Everyone is already locked: keep the saved state so the lockdown can still be undone.
                        LOG.warn("Failed to finish locking channel {} in guild {}", channelId, guildId, throwable)
                        LockResult.PartiallyLocked(rootMessage(throwable))
                    }
                }
            }
    }

    private fun lockOthers(
        channel: StandardGuildMessageChannel,
        otherEdits: List<LockdownPlanner.OverrideEdit>
    ): CompletableFuture<LockResult> {
        val guildId = channel.guild.idLong
        val channelId = channel.idLong
        val attempts = otherEdits.map { edit ->
            applyEdit(channel, edit).handle { _, throwable -> if (throwable == null) null else edit }
        }
        return CompletableFuture.allOf(*attempts.toTypedArray()).thenApply {
            val failed = attempts.mapNotNull { it.join() }
            failed.forEach {
                channelLockdownRepository.deleteById(
                    ChannelLockdown.ChannelLockdownId(guildId, channelId, it.targetType, it.targetId)
                )
            }
            LockResult.Locked(
                failed.filter { it.targetType == LockdownTargetType.ROLE }.map { it.targetId },
                failed.filter { it.targetType == LockdownTargetType.MEMBER }.map { it.targetId }
            )
        }
    }

    private fun applyEdit(
        channel: StandardGuildMessageChannel,
        edit: LockdownPlanner.OverrideEdit
    ): CompletableFuture<*> {
        return applyOverride(channel, edit.targetType, edit.targetId, edit.newAllow, edit.newDeny)
    }

    @Transactional
    fun unlock(channel: StandardGuildMessageChannel): CompletableFuture<UnlockResult> {
        val guildId = channel.guild.idLong
        val channelId = channel.idLong
        val key = guildId to channelId

        // A lock that is still applying its overrides must finish first, otherwise its later edits would be applied
        // after the saved state was already removed.
        if (!channelsInProgress.add(key)) {
            return CompletableFuture.completedFuture(UnlockResult.InProgress)
        }

        try {
            val savedStates = channelLockdownRepository.findAllByGuildIdAndChannelId(guildId, channelId)
            if (savedStates.isEmpty()) {
                channelsInProgress.remove(key)
                return CompletableFuture.completedFuture(UnlockResult.NotLocked)
            }

            return restoreAll(channel, savedStates).whenComplete { _, _ -> channelsInProgress.remove(key) }
        } catch (e: Exception) {
            channelsInProgress.remove(key)
            throw e
        }
    }

    private fun restoreAll(
        channel: StandardGuildMessageChannel,
        savedStates: List<ChannelLockdown>
    ): CompletableFuture<UnlockResult> {
        val guildId = channel.guild.idLong
        val channelId = channel.idLong
        val currentOverrides = channel.permissionOverrides
        val attempts = savedStates.map { saved ->
            val id = ChannelLockdown.ChannelLockdownId(guildId, channelId, saved.targetType, saved.targetId)
            val current = currentOverrides.firstOrNull {
                it.isRoleOverride == (saved.targetType == LockdownTargetType.ROLE) && it.idLong == saved.targetId
            }

            val action: CompletableFuture<*> = try {
                restore(channel, saved, current)
            } catch (e: Exception) {
                // JDA can throw straight away, for example when permissions are missing. One target failing must not
                // stop the others from being restored.
                CompletableFuture.failedFuture<Any>(e)
            }

            // The saved state is dropped even when restoring fails, otherwise the channel would stay "locked"
            // forever (for example when the role was deleted). Failures are reported to the moderator instead.
            action.handle { _, throwable ->
                if (throwable != null) {
                    LOG.warn("Failed to restore override {} while unlocking", id, throwable)
                }
                channelLockdownRepository.deleteById(id)
                if (throwable == null) null else id
            }
        }

        return CompletableFuture.allOf(*attempts.toTypedArray())
            .thenApply { UnlockResult.Unlocked(attempts.mapNotNull { it.join() }) }
    }

    private fun restore(
        channel: StandardGuildMessageChannel,
        saved: ChannelLockdown,
        current: PermissionOverride?
    ): CompletableFuture<*> {
        val restore = LockdownPlanner.planUnlock(
            LockdownPlanner.SavedState(
                saved.targetId, saved.targetType, saved.previousAllow, saved.previousDeny, saved.existed
            ),
            current?.let { toSnapshot(it) }
        )

        return when (restore) {
            LockdownPlanner.Restore.NoChange -> CompletableFuture.completedFuture(null)
            LockdownPlanner.Restore.Delete ->
                current?.delete()?.submit() ?: CompletableFuture.completedFuture(null)

            is LockdownPlanner.Restore.Update ->
                applyOverride(channel, saved.targetType, saved.targetId, restore.allow, restore.deny)
        }
    }

    @Transactional
    fun clearGuildState(guildId: Long) {
        channelLockdownRepository.deleteAllByGuildId(guildId)
    }

    private fun applyOverride(
        channel: StandardGuildMessageChannel,
        type: LockdownTargetType,
        id: Long,
        allow: Long,
        deny: Long
    ): CompletableFuture<*> {
        val holder = holderFor(channel, type, id)
            ?: return CompletableFuture.failedFuture<Any>(IllegalStateException("Target $id no longer available"))
        return try {
            channel.upsertPermissionOverride(holder).setPermissions(allow, deny).submit()
        } catch (e: Exception) {
            CompletableFuture.failedFuture<Any>(e)
        }
    }

    private fun holderFor(
        channel: StandardGuildMessageChannel,
        type: LockdownTargetType,
        id: Long
    ): IPermissionHolder? {
        val guild = channel.guild
        return when (type) {
            LockdownTargetType.ROLE -> if (id == guild.idLong) guild.publicRole else guild.getRoleById(id)
            LockdownTargetType.MEMBER -> guild.getMemberById(id)
        }
    }

    private fun toSnapshot(override: PermissionOverride): LockdownPlanner.OverrideSnapshot {
        val holder = override.permissionHolder
        val exempt = holder != null && (holder.hasPermission(Permission.ADMINISTRATOR) ||
                holder.hasPermission(Permission.MANAGE_CHANNEL))
        return LockdownPlanner.OverrideSnapshot(
            targetId = override.idLong,
            targetType = if (override.isRoleOverride) LockdownTargetType.ROLE else LockdownTargetType.MEMBER,
            allow = override.allowedRaw,
            deny = override.deniedRaw,
            exempt = exempt
        )
    }

    private fun rootMessage(throwable: Throwable): String {
        var cause = throwable
        while (cause.cause != null && cause.cause !== cause) cause = cause.cause!!
        return cause.message ?: cause.javaClass.simpleName
    }
}
