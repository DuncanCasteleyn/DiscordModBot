package be.duncanc.discordmodbot.moderation

import be.duncanc.discordmodbot.moderation.persistence.ChannelLockdown
import be.duncanc.discordmodbot.moderation.persistence.ChannelLockdownRepository
import be.duncanc.discordmodbot.moderation.persistence.LockdownTargetType
import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.entities.IPermissionHolder
import net.dv8tion.jda.api.entities.PermissionOverride
import net.dv8tion.jda.api.entities.channel.middleman.StandardGuildMessageChannel
import net.dv8tion.jda.api.exceptions.ErrorResponseException
import net.dv8tion.jda.api.requests.ErrorResponse
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

        /** Discord answers with one of these when the override or what it belongs to no longer exists. */
        private val TARGET_GONE_ERRORS = setOf(
            ErrorResponse.UNKNOWN_ROLE,
            ErrorResponse.UNKNOWN_MEMBER,
            ErrorResponse.UNKNOWN_USER,
            ErrorResponse.UNKNOWN_OVERRIDE,
            ErrorResponse.UNKNOWN_CHANNEL
        )
    }

    private class TargetGoneException(message: String) : RuntimeException(message)

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
        data object Unlocked : UnlockResult

        /** These targets could not be restored. Their saved state is kept, so unlocking again retries them. */
        data class PartiallyUnlocked(val failedTargets: List<ChannelLockdown.ChannelLockdownId>) : UnlockResult
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

            val guild = channel.guild
            val snapshots = channel.permissionOverrides
                // The override of a member who left the server cannot be checked or edited.
                .filter { it.permissionHolder != null }
                .map { toSnapshot(it) }
            val self = guild.selfMember
            val selfPermissions = if (self.hasPermission(Permission.ADMINISTRATOR)) {
                0L
            } else {
                Permission.getRaw(self.getPermissions(channel))
            }
            val plan = LockdownPlanner.planLock(guild.publicRole.idLong, snapshots, self.idLong, selfPermissions)

            channelLockdownRepository.saveAll(plan.edits.map {
                ChannelLockdown(
                    guildId, channelId, it.targetType, it.targetId, it.previousAllow, it.previousDeny, it.existed
                )
            })

            return lockEveryoneThenOthers(channel, plan)
                .whenComplete { _, _ -> channelsInProgress.remove(key) }
        } catch (e: Exception) {
            channelsInProgress.remove(key)
            throw e
        }
    }

    private fun lockEveryoneThenOthers(
        channel: StandardGuildMessageChannel,
        plan: LockdownPlanner.LockPlan
    ): CompletableFuture<LockResult> {
        val protect = plan.protect
        val everyoneEdit = plan.lockEdits.first()
        val otherEdits = plan.lockEdits.drop(1)

        // The bot has to keep its own permissions before everyone is denied, otherwise it could no longer edit the
        // channel, and with that no longer unlock it.
        val protectAttempt: CompletableFuture<Throwable?> = (protect?.let { applyEdit(channel, it) }
            ?: CompletableFuture.completedFuture(null)).handle { _, throwable -> throwable }

        return protectAttempt.thenCompose<LockResult> { protectError ->
            if (protectError != null) {
                abortLock(channel, null, protectError)
            } else {
                applyEdit(channel, everyoneEdit).handle<Throwable?> { _, throwable -> throwable }
                    .thenCompose<LockResult> { everyoneError ->
                        if (everyoneError != null) {
                            abortLock(channel, protect, everyoneError)
                        } else {
                            lockOthers(channel, otherEdits).exceptionally { throwable ->
                                // Everyone is already locked: keep the saved state so the lockdown can be undone.
                                LOG.warn("Failed to finish locking channel {}", channel.id, throwable)
                                LockResult.PartiallyLocked(rootMessage(throwable))
                            }
                        }
                    }
            }
        }
    }

    /**
     * Gives up a lockdown that did not lock everyone: puts the bot's own override back (when it was changed) and
     * forgets the saved state, as nothing else was changed.
     */
    private fun abortLock(
        channel: StandardGuildMessageChannel,
        appliedProtect: LockdownPlanner.OverrideEdit?,
        error: Throwable
    ): CompletableFuture<LockResult> {
        LOG.warn("Failed to lock channel {} in guild {}", channel.id, channel.guild.id, error)
        val undo: CompletableFuture<*> = appliedProtect?.let {
            applyOverride(channel, it.targetType, it.targetId, it.previousAllow, it.previousDeny)
        } ?: CompletableFuture.completedFuture(null)

        return undo.handle<LockResult> { _, _ ->
            channelLockdownRepository.deleteAllByGuildIdAndChannelId(channel.guild.idLong, channel.idLong)
            LockResult.Failed(rootMessage(error))
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
        val selfId = channel.guild.selfMember.idLong
        val (own, others) = savedStates.partition {
            it.targetType == LockdownTargetType.MEMBER && it.targetId == selfId
        }
        val currentOverrides = channel.permissionOverrides

        val otherAttempts = others.map { restoreOne(channel, it, currentOverrides) }
        return CompletableFuture.allOf(*otherAttempts.toTypedArray())
            .thenCompose {
                val failed = otherAttempts.mapNotNull { it.join() }
                if (failed.isNotEmpty() || own.isEmpty()) {
                    // The bot keeps its own override while something still has to be restored, so a retry can work.
                    CompletableFuture.completedFuture(failed)
                } else {
                    val ownAttempts = own.map { restoreOne(channel, it, currentOverrides) }
                    CompletableFuture.allOf(*ownAttempts.toTypedArray())
                        .thenApply { ownAttempts.mapNotNull { it.join() } }
                }
            }
            .thenApply { failed ->
                if (failed.isEmpty()) UnlockResult.Unlocked else UnlockResult.PartiallyUnlocked(failed)
            }
    }

    /**
     * Restores one saved override. The result is the id of the override when it could not be restored and its saved
     * state was kept so unlocking can be retried, and `null` when it is done or when its target is gone for good.
     */
    private fun restoreOne(
        channel: StandardGuildMessageChannel,
        saved: ChannelLockdown,
        currentOverrides: List<PermissionOverride>
    ): CompletableFuture<ChannelLockdown.ChannelLockdownId?> {
        val id = ChannelLockdown.ChannelLockdownId(saved.guildId, saved.channelId, saved.targetType, saved.targetId)
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

        return action.handle<ChannelLockdown.ChannelLockdownId?> { _, throwable ->
            when {
                throwable == null -> {
                    channelLockdownRepository.deleteById(id)
                    null
                }

                isTargetGone(throwable) -> {
                    LOG.info("Override {} no longer exists, dropping its saved state", id)
                    channelLockdownRepository.deleteById(id)
                    null
                }

                else -> {
                    LOG.warn("Failed to restore override {} while unlocking, keeping its saved state", id, throwable)
                    id
                }
            }
        }
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
            ?: return CompletableFuture.failedFuture<Any>(TargetGoneException("Target $id no longer available"))
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
            LockdownTargetType.MEMBER ->
                if (id == guild.selfMember.idLong) guild.selfMember else guild.getMemberById(id)
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

    private fun isTargetGone(throwable: Throwable): Boolean {
        var cause: Throwable? = throwable
        while (cause != null) {
            if (cause is TargetGoneException) return true
            if (cause is ErrorResponseException && cause.errorResponse in TARGET_GONE_ERRORS) return true
            val next = cause.cause
            cause = if (next === cause) null else next
        }
        return false
    }

    private fun rootMessage(throwable: Throwable): String {
        var cause = throwable
        while (cause.cause != null && cause.cause !== cause) cause = cause.cause!!
        return cause.message ?: cause.javaClass.simpleName
    }
}
