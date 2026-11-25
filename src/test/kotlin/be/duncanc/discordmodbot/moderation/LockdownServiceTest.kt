package be.duncanc.discordmodbot.moderation

import be.duncanc.discordmodbot.moderation.persistence.ChannelLockdown
import be.duncanc.discordmodbot.moderation.persistence.ChannelLockdownRepository
import be.duncanc.discordmodbot.moderation.persistence.LockdownTargetType
import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.PermissionOverride
import net.dv8tion.jda.api.entities.Role
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel
import net.dv8tion.jda.api.requests.restaction.AuditableRestAction
import net.dv8tion.jda.api.requests.restaction.PermissionOverrideAction
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

@ExtendWith(MockitoExtension::class)
class LockdownServiceTest {
    companion object {
        private const val GUILD_ID = 100L
        private const val CHANNEL_ID = 200L
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

    private lateinit var service: LockdownService

    @BeforeEach
    fun setUp() {
        service = LockdownService(repository)
        whenever(channel.guild).thenReturn(guild)
        whenever(guild.idLong).thenReturn(GUILD_ID)
        whenever(channel.idLong).thenReturn(CHANNEL_ID)
    }

    private fun stubEmptyChannel() {
        whenever(guild.publicRole).thenReturn(publicRole)
        whenever(publicRole.idLong).thenReturn(GUILD_ID)
        whenever(channel.permissionOverrides).thenReturn(emptyList())
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
    fun `locking a channel while a lock is still in progress reports already locked`() {
        stubEmptyChannel()
        whenever(upsertAction.submit()).thenReturn(CompletableFuture())

        service.lock(channel)
        val second = service.lock(channel).get(5, TimeUnit.SECONDS)

        assertEquals(LockdownService.LockResult.AlreadyLocked, second)
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
        whenever(channel.upsertPermissionOverride(publicRole)).thenReturn(upsertAction)
        whenever(upsertAction.setPermissions(any<Long>(), any<Long>())).thenReturn(upsertAction)
        whenever(upsertAction.submit()).thenReturn(CompletableFuture.completedFuture(mock<PermissionOverride>()))
        whenever(guild.getRoleById(7L)).thenReturn(otherRole)
        whenever(channel.upsertPermissionOverride(otherRole)).thenReturn(failingAction)
        whenever(failingAction.setPermissions(any<Long>(), any<Long>())).thenReturn(failingAction)
        whenever(failingAction.submit()).thenReturn(CompletableFuture.failedFuture(IllegalStateException("denied")))
        whenever(repository.deleteById(any())).thenThrow(IllegalStateException("database down"))

        val result = service.lock(channel).get()

        assertTrue(result is LockdownService.LockResult.Failed)
        verify(repository, never()).deleteAllByGuildIdAndChannelId(any(), any())
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

        assertEquals(LockdownService.UnlockResult.Unlocked(emptyList()), result)
        verify(everyoneOverride).delete()
        verify(repository).deleteById(
            ChannelLockdown.ChannelLockdownId(GUILD_ID, CHANNEL_ID, LockdownTargetType.ROLE, GUILD_ID)
        )
    }
}
