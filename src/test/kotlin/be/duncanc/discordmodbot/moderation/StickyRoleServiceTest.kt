package be.duncanc.discordmodbot.moderation

import be.duncanc.discordmodbot.logging.GuildLogger
import be.duncanc.discordmodbot.moderation.persistence.StickyRoleConfig
import be.duncanc.discordmodbot.moderation.persistence.StickyRoleConfigRepository
import be.duncanc.discordmodbot.moderation.persistence.StickyRoleSnapshot
import be.duncanc.discordmodbot.moderation.persistence.StickyRoleSnapshotRepository
import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.Member
import net.dv8tion.jda.api.entities.Role
import net.dv8tion.jda.api.entities.SelfMember
import net.dv8tion.jda.api.entities.User
import net.dv8tion.jda.api.requests.restaction.AuditableRestAction
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.function.Consumer
import java.util.Optional
import kotlin.test.assertEquals

@ExtendWith(MockitoExtension::class)
class StickyRoleServiceTest {
    @Mock
    private lateinit var stickyRoleConfigRepository: StickyRoleConfigRepository

    @Mock
    private lateinit var stickyRoleSnapshotRepository: StickyRoleSnapshotRepository

    @Mock
    private lateinit var guildLogger: GuildLogger

    @Mock
    private lateinit var guild: Guild

    @Mock
    private lateinit var member: Member

    @Mock
    private lateinit var selfMember: SelfMember

    @Mock
    private lateinit var role: Role

    @Mock
    private lateinit var user: User

    @Mock
    private lateinit var roleUpdateAction: AuditableRestAction<Void>

    private lateinit var service: StickyRoleService

    @BeforeEach
    fun setUp() {
        service = StickyRoleService(stickyRoleConfigRepository, stickyRoleSnapshotRepository, guildLogger)
    }

    @Test
    fun `capture roles on leave stores configured roles only`() {
        whenever(stickyRoleConfigRepository.findById(1L)).thenReturn(
            Optional.of(StickyRoleConfig(1L, mutableSetOf(11L, 12L)))
        )

        service.captureRolesOnLeave(1L, 99L, listOf(10L, 11L, 13L))

        val snapshotCaptor = argumentCaptor<StickyRoleSnapshot>()
        verify(stickyRoleSnapshotRepository).save(snapshotCaptor.capture())
        assertEquals(1L, snapshotCaptor.firstValue.guildId)
        assertEquals(99L, snapshotCaptor.firstValue.userId)
        assertEquals(setOf(11L), snapshotCaptor.firstValue.roleIds)
    }

    @Test
    fun `capture roles on leave deletes snapshot when no roles match`() {
        whenever(stickyRoleConfigRepository.findById(1L)).thenReturn(
            Optional.of(StickyRoleConfig(1L, mutableSetOf(11L)))
        )

        service.captureRolesOnLeave(1L, 99L, listOf(10L))

        verify(stickyRoleSnapshotRepository).deleteById(StickyRoleSnapshot.StickyRoleSnapshotId(1L, 99L))
        verify(stickyRoleSnapshotRepository, never()).save(any())
    }

    @Test
    fun `restore roles on join reapplies restorable sticky roles and logs action`() {
        val snapshotId = StickyRoleSnapshot.StickyRoleSnapshotId(1L, 99L)
        whenever(stickyRoleSnapshotRepository.findById(snapshotId)).thenReturn(
            Optional.of(StickyRoleSnapshot(1L, 99L, mutableSetOf(11L, 12L)))
        )
        whenever(stickyRoleConfigRepository.findById(1L)).thenReturn(
            Optional.of(StickyRoleConfig(1L, mutableSetOf(11L, 12L)))
        )
        whenever(guild.idLong).thenReturn(1L)
        whenever(member.idLong).thenReturn(99L)
        whenever(member.roles).thenReturn(emptyList())
        whenever(guild.getRoleById(11L)).thenReturn(role)
        whenever(guild.getRoleById(12L)).thenReturn(null)
        whenever(role.isPublicRole).thenReturn(false)
        whenever(role.isManaged).thenReturn(false)
        whenever(role.guild).thenReturn(guild)
        whenever(guild.selfMember).thenReturn(selfMember)
        whenever(selfMember.hasPermission(Permission.MANAGE_ROLES)).thenReturn(true)
        whenever(selfMember.canInteract(role)).thenReturn(true)
        whenever(guild.modifyMemberRoles(member, listOf(role), null)).thenReturn(roleUpdateAction)
        whenever(roleUpdateAction.reason(any())).thenReturn(roleUpdateAction)

        service.restoreRolesOnJoin(guild, member)

        verify(guild).modifyMemberRoles(member, listOf(role), null)
        val successCaptor = argumentCaptor<Consumer<Void>>()
        val failureCaptor = argumentCaptor<Consumer<Throwable>>()
        verify(roleUpdateAction).queue(successCaptor.capture(), failureCaptor.capture())
        verify(stickyRoleSnapshotRepository, never()).deleteById(snapshotId)
    }

    @Test
    fun `restore roles on join keeps snapshot when role restore fails`() {
        val snapshotId = StickyRoleSnapshot.StickyRoleSnapshotId(1L, 99L)
        whenever(stickyRoleSnapshotRepository.findById(snapshotId)).thenReturn(
            Optional.of(StickyRoleSnapshot(1L, 99L, mutableSetOf(11L)))
        )
        whenever(stickyRoleConfigRepository.findById(1L)).thenReturn(
            Optional.of(StickyRoleConfig(1L, mutableSetOf(11L)))
        )
        whenever(guild.idLong).thenReturn(1L)
        whenever(member.idLong).thenReturn(99L)
        whenever(member.roles).thenReturn(emptyList())
        whenever(guild.getRoleById(11L)).thenReturn(role)
        whenever(role.isPublicRole).thenReturn(false)
        whenever(role.isManaged).thenReturn(false)
        whenever(role.guild).thenReturn(guild)
        whenever(guild.selfMember).thenReturn(selfMember)
        whenever(selfMember.hasPermission(Permission.MANAGE_ROLES)).thenReturn(true)
        whenever(selfMember.canInteract(role)).thenReturn(true)
        whenever(guild.modifyMemberRoles(member, listOf(role), null)).thenReturn(roleUpdateAction)
        whenever(roleUpdateAction.reason(any())).thenReturn(roleUpdateAction)

        service.restoreRolesOnJoin(guild, member)

        val failureCaptor = argumentCaptor<Consumer<Throwable>>()
        verify(roleUpdateAction).queue(any(), failureCaptor.capture())

        failureCaptor.firstValue.accept(RuntimeException("restore failed"))

        verify(stickyRoleSnapshotRepository, never()).deleteById(snapshotId)
    }

    @Test
    fun `remove configured role also strips it from saved snapshots`() {
        whenever(stickyRoleConfigRepository.findById(1L)).thenReturn(
            Optional.of(StickyRoleConfig(1L, mutableSetOf(11L, 12L)))
        )
        whenever(stickyRoleSnapshotRepository.findAllByGuildId(1L)).thenReturn(
            listOf(
                StickyRoleSnapshot(1L, 99L, mutableSetOf(11L, 13L)),
                StickyRoleSnapshot(1L, 100L, mutableSetOf(11L))
            )
        )

        service.removeConfiguredRole(1L, 11L)

        val configCaptor = argumentCaptor<StickyRoleConfig>()
        verify(stickyRoleConfigRepository).save(configCaptor.capture())
        assertEquals(setOf(12L), configCaptor.firstValue.roleIds)

        val snapshotCaptor = argumentCaptor<StickyRoleSnapshot>()
        verify(stickyRoleSnapshotRepository).save(snapshotCaptor.capture())
        assertEquals(setOf(13L), snapshotCaptor.firstValue.roleIds)
        verify(stickyRoleSnapshotRepository).delete(StickyRoleSnapshot(1L, 100L, mutableSetOf()))
    }

    @Test
    fun `clear configured roles removes config and snapshots`() {
        service.clearConfiguredRoles(1L)

        verify(stickyRoleConfigRepository).deleteById(1L)
        verify(stickyRoleSnapshotRepository).deleteAllByGuildId(1L)
    }
}
