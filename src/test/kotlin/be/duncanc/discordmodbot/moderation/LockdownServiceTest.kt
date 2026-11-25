package be.duncanc.discordmodbot.moderation

import be.duncanc.discordmodbot.moderation.persistence.ChannelLockdown
import be.duncanc.discordmodbot.moderation.persistence.ChannelLockdownRepository
import be.duncanc.discordmodbot.moderation.persistence.LockdownTargetType
import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.PermissionOverride
import net.dv8tion.jda.api.entities.Role
import net.dv8tion.jda.api.entities.SelfMember
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel
import net.dv8tion.jda.api.exceptions.ErrorResponseException
import net.dv8tion.jda.api.requests.ErrorResponse
import net.dv8tion.jda.api.requests.restaction.AuditableRestAction
import net.dv8tion.jda.api.requests.restaction.PermissionOverrideAction
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.Mockito
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

@ExtendWith(MockitoExtension::class)
class LockdownServiceTest {
    companion object {
        private const val GUILD_ID = 100L
        private const val CHANNEL_ID = 200L
        private const val SELF_ID = 300L
    }

    @Mock
    private lateinit var repository: ChannelLockdownRepository

    @Mock
    private lateinit var channel: TextChannel

    @Mock
    private lateinit var guild: Guild

    @Mock
    private lateinit var publicRole: Role

    @Mock
    private lateinit var upsertAction: PermissionOverrideAction

    @Mock
    private lateinit var selfMember: SelfMember

    private lateinit var service: LockdownService

    @BeforeEach
    fun setUp() {
        service = LockdownService(repository)
        whenever(channel.guild).thenReturn(guild)
        whenever(guild.idLong).thenReturn(GUILD_ID)
        whenever(channel.idLong).thenReturn(CHANNEL_ID)
        Mockito.lenient().`when`(guild.selfMember).thenReturn(selfMember)
        Mockito.lenient().`when`(selfMember.idLong).thenReturn(SELF_ID)
    }

    private fun stubSelfPermissions(vararg permissions: Permission) {
        whenever(selfMember.getPermissions(channel)).thenReturn(
            if (permissions.isEmpty()) java.util.EnumSet.noneOf(Permission::class.java)
            else java.util.EnumSet.copyOf(permissions.toList())
        )
    }

    private fun stubEmptyChannel() {
        whenever(guild.publicRole).thenReturn(publicRole)
        whenever(publicRole.idLong).thenReturn(GUILD_ID)
        whenever(channel.permissionOverrides).thenReturn(emptyList())
        stubSelfPermissions()
        whenever(channel.upsertPermissionOverride(publicRole)).thenReturn(upsertAction)
        whenever(upsertAction.setPermissions(any<Long>(), any<Long>())).thenReturn(upsertAction)
    }

    @Test
    fun `locking an already locked channel changes nothing`() {
        whenever(repository.existsByGuildIdAndChannelId(GUILD_ID, CHANNEL_ID)).thenReturn(true)

        val result = service.lock(channel).get()

        assertEquals(LockdownService.LockResult.AlreadyLocked, result)
        verify(channel, never()).upsertPermissionOverride(any())
        verify(repository, never()).saveAll(any<Iterable<ChannelLockdown>>())
    }

    @Test
    fun `locking a channel without overrides denies everyone and saves that it did not exist`() {
        stubEmptyChannel()
        whenever(upsertAction.submit()).thenReturn(CompletableFuture.completedFuture(mock<PermissionOverride>()))

        val result = service.lock(channel).get()

        assertEquals(LockdownService.LockResult.Locked(emptyList(), emptyList()), result)
        verify(upsertAction).setPermissions(0L, LockdownPlanner.LOCKED_MASK)
        argumentCaptor<Iterable<ChannelLockdown>>().apply {
            verify(repository).saveAll(capture())
            val saved = firstValue.single()
            assertEquals(GUILD_ID, saved.targetId)
            assertEquals(LockdownTargetType.ROLE, saved.targetType)
            assertEquals(false, saved.existed)
        }
    }

    @Test
    fun `locking fails and forgets the saved state when everyone cannot be edited`() {
        stubEmptyChannel()
        whenever(upsertAction.submit())
            .thenReturn(CompletableFuture.failedFuture(IllegalStateException("Missing permissions")))

        val result = service.lock(channel).get()

        assertTrue(result is LockdownService.LockResult.Failed)
        verify(repository).deleteAllByGuildIdAndChannelId(GUILD_ID, CHANNEL_ID)
    }

    @Test
    fun `locking a channel while another lock is still in progress reports it as busy`() {
        stubEmptyChannel()
        whenever(upsertAction.submit()).thenReturn(CompletableFuture())

        service.lock(channel)
        val second = service.lock(channel).get(5, TimeUnit.SECONDS)

        assertEquals(LockdownService.LockResult.InProgress, second)
        verify(channel, times(1)).upsertPermissionOverride(any())
        verify(repository, times(1)).saveAll(any<Iterable<ChannelLockdown>>())
    }

    @Test
    fun `a failure after everyone was locked keeps the saved state so the channel can still be unlocked`() {
        val roleOverride = mock<PermissionOverride>()
        val otherRole = mock<Role>()
        val failingAction = mock<PermissionOverrideAction>()
        whenever(guild.publicRole).thenReturn(publicRole)
        whenever(publicRole.idLong).thenReturn(GUILD_ID)
        whenever(roleOverride.isRoleOverride).thenReturn(true)
        whenever(roleOverride.idLong).thenReturn(7L)
        whenever(roleOverride.allowedRaw).thenReturn(Permission.getRaw(Permission.MESSAGE_SEND))
        whenever(roleOverride.deniedRaw).thenReturn(0L)
        whenever(roleOverride.permissionHolder).thenReturn(otherRole)
        whenever(channel.permissionOverrides).thenReturn(listOf(roleOverride))
        stubSelfPermissions()
        whenever(channel.upsertPermissionOverride(publicRole)).thenReturn(upsertAction)
        whenever(upsertAction.setPermissions(any<Long>(), any<Long>())).thenReturn(upsertAction)
        whenever(upsertAction.submit()).thenReturn(CompletableFuture.completedFuture(mock<PermissionOverride>()))
        whenever(guild.getRoleById(7L)).thenReturn(otherRole)
        whenever(channel.upsertPermissionOverride(otherRole)).thenReturn(failingAction)
        whenever(failingAction.setPermissions(any<Long>(), any<Long>())).thenReturn(failingAction)
        whenever(failingAction.submit()).thenReturn(CompletableFuture.failedFuture(IllegalStateException("denied")))
        whenever(repository.deleteById(any())).thenThrow(IllegalStateException("database down"))

        val result = service.lock(channel).get()

        assertEquals(LockdownService.LockResult.PartiallyLocked("database down"), result)
        verify(repository, never()).deleteAllByGuildIdAndChannelId(any(), any())
    }

    @Test
    fun `unlocking while a lock is still in progress reports it as busy and changes nothing`() {
        stubEmptyChannel()
        whenever(upsertAction.submit()).thenReturn(CompletableFuture())

        service.lock(channel)
        val result = service.unlock(channel).get(5, TimeUnit.SECONDS)

        assertEquals(LockdownService.UnlockResult.InProgress, result)
        verify(repository, never()).findAllByGuildIdAndChannelId(any(), any())
    }

    @Test
    fun `unlocking keeps the saved state of a target that could not be restored and restores the others`() {
        val everyoneOverride = stubSavedRoleOverride(GUILD_ID)
        val roleOverride = stubSavedRoleOverride(7L)
        val deleteAction = mock<AuditableRestAction<Void>>()
        whenever(everyoneOverride.delete()).thenThrow(IllegalStateException("Missing permissions"))
        whenever(roleOverride.delete()).thenReturn(deleteAction)
        whenever(deleteAction.submit()).thenReturn(CompletableFuture.completedFuture(null))
        whenever(channel.permissionOverrides).thenReturn(listOf(everyoneOverride, roleOverride))
        whenever(repository.findAllByGuildIdAndChannelId(GUILD_ID, CHANNEL_ID)).thenReturn(
            listOf(
                ChannelLockdown(GUILD_ID, CHANNEL_ID, LockdownTargetType.ROLE, GUILD_ID, 0L, 0L, false),
                ChannelLockdown(GUILD_ID, CHANNEL_ID, LockdownTargetType.ROLE, 7L, 0L, 0L, false)
            )
        )

        val result = service.unlock(channel).get(5, TimeUnit.SECONDS)

        val everyoneId = ChannelLockdown.ChannelLockdownId(GUILD_ID, CHANNEL_ID, LockdownTargetType.ROLE, GUILD_ID)
        val roleId = ChannelLockdown.ChannelLockdownId(GUILD_ID, CHANNEL_ID, LockdownTargetType.ROLE, 7L)
        assertEquals(LockdownService.UnlockResult.PartiallyUnlocked(listOf(everyoneId)), result)
        verify(roleOverride).delete()
        verify(repository, never()).deleteById(everyoneId)
        verify(repository).deleteById(roleId)
    }

    @Test
    fun `unlocking drops the saved state of a target that no longer exists`() {
        val roleOverride = stubSavedRoleOverride(7L)
        whenever(roleOverride.allowedRaw).thenReturn(0L)
        whenever(channel.permissionOverrides).thenReturn(listOf(roleOverride))
        whenever(guild.getRoleById(7L)).thenReturn(null)
        whenever(repository.findAllByGuildIdAndChannelId(GUILD_ID, CHANNEL_ID)).thenReturn(
            listOf(
                ChannelLockdown(
                    GUILD_ID, CHANNEL_ID, LockdownTargetType.ROLE, 7L,
                    Permission.getRaw(Permission.MESSAGE_SEND), 0L, true
                )
            )
        )

        val result = service.unlock(channel).get(5, TimeUnit.SECONDS)

        assertEquals(LockdownService.UnlockResult.Unlocked, result)
        verify(repository).deleteById(
            ChannelLockdown.ChannelLockdownId(GUILD_ID, CHANNEL_ID, LockdownTargetType.ROLE, 7L)
        )
    }

    @Test
    fun `unlocking drops the saved state when Discord says the role is unknown`() {
        val roleOverride = stubSavedRoleOverride(7L)
        val unknownRole = mock<ErrorResponseException> { on { errorResponse } doReturn ErrorResponse.UNKNOWN_ROLE }
        val deleteAction = mock<AuditableRestAction<Void>>()
        whenever(roleOverride.delete()).thenReturn(deleteAction)
        whenever(deleteAction.submit()).thenReturn(CompletableFuture.failedFuture(unknownRole))
        whenever(channel.permissionOverrides).thenReturn(listOf(roleOverride))
        whenever(repository.findAllByGuildIdAndChannelId(GUILD_ID, CHANNEL_ID)).thenReturn(
            listOf(ChannelLockdown(GUILD_ID, CHANNEL_ID, LockdownTargetType.ROLE, 7L, 0L, 0L, false))
        )

        val result = service.unlock(channel).get(5, TimeUnit.SECONDS)

        assertEquals(LockdownService.UnlockResult.Unlocked, result)
        verify(repository).deleteById(
            ChannelLockdown.ChannelLockdownId(GUILD_ID, CHANNEL_ID, LockdownTargetType.ROLE, 7L)
        )
    }

    @Test
    fun `locking lets the bot keep its own permissions before everyone is denied`() {
        val selfOverrideAction = mock<PermissionOverrideAction>()
        stubEmptyChannel()
        stubSelfPermissions(Permission.MESSAGE_SEND, Permission.VIEW_CHANNEL)
        whenever(channel.upsertPermissionOverride(selfMember)).thenReturn(selfOverrideAction)
        whenever(selfOverrideAction.setPermissions(any<Long>(), any<Long>())).thenReturn(selfOverrideAction)
        whenever(selfOverrideAction.submit())
            .thenReturn(CompletableFuture.completedFuture(mock<PermissionOverride>()))
        whenever(upsertAction.submit()).thenReturn(CompletableFuture.completedFuture(mock<PermissionOverride>()))

        val result = service.lock(channel).get(5, TimeUnit.SECONDS)

        assertEquals(LockdownService.LockResult.Locked(emptyList(), emptyList()), result)
        inOrder(channel, selfOverrideAction, upsertAction) {
            verify(selfOverrideAction).setPermissions(Permission.getRaw(Permission.MESSAGE_SEND), 0L)
            verify(upsertAction).setPermissions(0L, LockdownPlanner.LOCKED_MASK)
        }
    }

    @Test
    fun `locking undoes the bot's own override again when everyone could not be locked`() {
        val selfOverrideAction = mock<PermissionOverrideAction>()
        stubEmptyChannel()
        stubSelfPermissions(Permission.MESSAGE_SEND)
        whenever(channel.upsertPermissionOverride(selfMember)).thenReturn(selfOverrideAction)
        whenever(selfOverrideAction.setPermissions(any<Long>(), any<Long>())).thenReturn(selfOverrideAction)
        whenever(selfOverrideAction.submit())
            .thenReturn(CompletableFuture.completedFuture(mock<PermissionOverride>()))
        whenever(upsertAction.submit())
            .thenReturn(CompletableFuture.failedFuture(IllegalStateException("Missing permissions")))

        val result = service.lock(channel).get(5, TimeUnit.SECONDS)

        assertTrue(result is LockdownService.LockResult.Failed)
        verify(selfOverrideAction).setPermissions(Permission.getRaw(Permission.MESSAGE_SEND), 0L)
        verify(selfOverrideAction).setPermissions(0L, 0L)
        verify(repository).deleteAllByGuildIdAndChannelId(GUILD_ID, CHANNEL_ID)
    }

    @Test
    fun `locking ignores overrides of members that left the server`() {
        val departedMember = mock<PermissionOverride>()
        stubEmptyChannel()
        whenever(channel.permissionOverrides).thenReturn(listOf(departedMember))
        whenever(upsertAction.submit()).thenReturn(CompletableFuture.completedFuture(mock<PermissionOverride>()))

        val result = service.lock(channel).get(5, TimeUnit.SECONDS)

        assertEquals(LockdownService.LockResult.Locked(emptyList(), emptyList()), result)
        verify(channel, times(1)).upsertPermissionOverride(any())
    }

    @Test
    fun `unlocking restores the bot's own override last`() {
        val everyoneOverride = stubSavedRoleOverride(GUILD_ID)
        val selfOverride = mock<PermissionOverride>()
        val everyoneAction = mock<PermissionOverrideAction>()
        val selfDelete = mock<AuditableRestAction<Void>>()
        whenever(selfOverride.isRoleOverride).thenReturn(false)
        whenever(selfOverride.idLong).thenReturn(SELF_ID)
        whenever(selfOverride.allowedRaw).thenReturn(Permission.getRaw(Permission.MESSAGE_SEND))
        whenever(selfOverride.deniedRaw).thenReturn(0L)
        whenever(selfOverride.delete()).thenReturn(selfDelete)
        whenever(selfDelete.submit()).thenReturn(CompletableFuture.completedFuture(null))
        whenever(guild.publicRole).thenReturn(publicRole)
        whenever(channel.upsertPermissionOverride(publicRole)).thenReturn(everyoneAction)
        whenever(everyoneAction.setPermissions(any<Long>(), any<Long>())).thenReturn(everyoneAction)
        whenever(everyoneAction.submit()).thenReturn(CompletableFuture.completedFuture(mock<PermissionOverride>()))
        whenever(everyoneOverride.allowedRaw).thenReturn(0L)
        whenever(channel.permissionOverrides).thenReturn(listOf(everyoneOverride, selfOverride))
        whenever(repository.findAllByGuildIdAndChannelId(GUILD_ID, CHANNEL_ID)).thenReturn(
            listOf(
                ChannelLockdown(GUILD_ID, CHANNEL_ID, LockdownTargetType.MEMBER, SELF_ID, 0L, 0L, false),
                ChannelLockdown(
                    GUILD_ID, CHANNEL_ID, LockdownTargetType.ROLE, GUILD_ID,
                    Permission.getRaw(Permission.MESSAGE_SEND), 0L, true
                )
            )
        )

        val result = service.unlock(channel).get(5, TimeUnit.SECONDS)

        assertEquals(LockdownService.UnlockResult.Unlocked, result)
        inOrder(everyoneAction, selfOverride) {
            verify(everyoneAction).submit()
            verify(selfOverride).delete()
        }
    }

    private fun stubSavedRoleOverride(roleId: Long): PermissionOverride {
        val override = mock<PermissionOverride>()
        whenever(override.isRoleOverride).thenReturn(true)
        whenever(override.idLong).thenReturn(roleId)
        whenever(override.allowedRaw).thenReturn(0L)
        whenever(override.deniedRaw).thenReturn(LockdownPlanner.LOCKED_MASK)
        return override
    }

    @Test
    fun `unlocking a channel that is not locked reports so`() {
        whenever(repository.findAllByGuildIdAndChannelId(GUILD_ID, CHANNEL_ID)).thenReturn(emptyList())

        val result = service.unlock(channel).get()

        assertEquals(LockdownService.UnlockResult.NotLocked, result)
    }

    @Test
    fun `unlocking deletes an everyone override that did not exist before and forgets the saved state`() {
        val everyoneOverride = mock<PermissionOverride>()
        val deleteAction = mock<AuditableRestAction<Void>>()
        whenever(everyoneOverride.isRoleOverride).thenReturn(true)
        whenever(everyoneOverride.idLong).thenReturn(GUILD_ID)
        whenever(everyoneOverride.allowedRaw).thenReturn(0L)
        whenever(everyoneOverride.deniedRaw).thenReturn(LockdownPlanner.LOCKED_MASK)
        whenever(everyoneOverride.delete()).thenReturn(deleteAction)
        whenever(deleteAction.submit()).thenReturn(CompletableFuture.completedFuture(null))
        whenever(channel.permissionOverrides).thenReturn(listOf(everyoneOverride))
        whenever(repository.findAllByGuildIdAndChannelId(GUILD_ID, CHANNEL_ID)).thenReturn(
            listOf(ChannelLockdown(GUILD_ID, CHANNEL_ID, LockdownTargetType.ROLE, GUILD_ID, 0L, 0L, false))
        )

        val result = service.unlock(channel).get()

        assertEquals(LockdownService.UnlockResult.Unlocked, result)
        verify(everyoneOverride).delete()
        verify(repository).deleteById(
            ChannelLockdown.ChannelLockdownId(GUILD_ID, CHANNEL_ID, LockdownTargetType.ROLE, GUILD_ID)
        )
    }
}
