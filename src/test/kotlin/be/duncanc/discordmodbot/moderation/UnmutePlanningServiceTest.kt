package be.duncanc.discordmodbot.moderation

import be.duncanc.discordmodbot.logging.GuildLogger
import be.duncanc.discordmodbot.moderation.persistence.MuteRole
import be.duncanc.discordmodbot.moderation.persistence.MuteRolesRepository
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.Member
import net.dv8tion.jda.api.entities.Role
import net.dv8tion.jda.api.entities.User
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.*
import java.time.OffsetDateTime
import java.util.*

@ExtendWith(MockitoExtension::class)
class UnmutePlanningServiceTest {
    @Mock
    private lateinit var muteService: MuteService

    @Mock
    private lateinit var scheduledUnmuteService: ScheduledUnmuteService

    @Mock
    private lateinit var guildLogger: GuildLogger

    @Mock
    private lateinit var muteRolesRepository: MuteRolesRepository

    @Mock
    private lateinit var guild: Guild

    @Mock
    private lateinit var moderator: Member

    @Mock
    private lateinit var moderatorUser: User

    @Mock
    private lateinit var targetMember: Member

    @Mock
    private lateinit var targetUser: User

    @Mock
    private lateinit var muteRole: Role

    private lateinit var service: UnmutePlanningService

    @BeforeEach
    fun setUp() {
        service = UnmutePlanningService(muteService, scheduledUnmuteService, guildLogger, muteRolesRepository)
    }

    @Test
    fun `plans an unmute for a muted user`() {
        val jda = mock<JDA>()

        whenever(guild.idLong).thenReturn(1L)
        whenever(muteRolesRepository.findById(1L)).thenReturn(Optional.of(MuteRole(1L, 2L)))
        whenever(guild.getRoleById(2L)).thenReturn(muteRole)
        whenever(guild.getMemberById(99L)).thenReturn(targetMember)
        whenever(targetMember.roles).thenReturn(listOf(muteRole))
        whenever(targetMember.nickname).thenReturn(null)
        whenever(targetMember.user).thenReturn(targetUser)
        whenever(targetUser.name).thenReturn("Target")
        whenever(guild.jda).thenReturn(jda)
        whenever(jda.getUserById(99L)).thenReturn(targetUser)
        whenever(guild.getMember(moderator)).thenReturn(moderator)
        whenever(moderator.nickname).thenReturn(null)
        whenever(moderator.user).thenReturn(moderatorUser)
        whenever(moderatorUser.name).thenReturn("Moderator")

        val plannedAt = service.planUnmute(guild, 99L, moderator, 2)

        verify(scheduledUnmuteService).planUnmute(eq(1L), eq(99L), any<OffsetDateTime>())
        verify(guildLogger).log(
            any(),
            eq(targetUser),
            eq(guild),
            isNull(),
            eq(GuildLogger.LogTypeAction.MODERATOR),
            isNull()
        )
        kotlin.test.assertTrue(plannedAt.isAfter(OffsetDateTime.now().plusDays(1)))
    }

    @Test
    fun `fails when the user is not muted`() {
        whenever(guild.idLong).thenReturn(1L)
        whenever(muteRolesRepository.findById(1L)).thenReturn(Optional.of(MuteRole(1L, 2L)))
        whenever(guild.getRoleById(2L)).thenReturn(muteRole)
        whenever(guild.getMemberById(99L)).thenReturn(null)
        whenever(muteService.isUserMuted(1L, 99L)).thenReturn(false)

        val exception = assertThrows<IllegalStateException> {
            service.planUnmute(guild, 99L, moderator, 2)
        }

        kotlin.test.assertEquals("This user is not muted.", exception.message)
    }

    @Test
    fun `fails when mute role is not configured`() {
        whenever(guild.idLong).thenReturn(1L)
        whenever(muteRolesRepository.findById(1L)).thenReturn(Optional.empty())

        val exception = assertThrows<IllegalStateException> {
            service.planUnmute(guild, 99L, moderator, 2)
        }

        kotlin.test.assertEquals("Mute role is not configured for this server.", exception.message)
    }

    @Test
    fun `fails when mute role does not exist in guild`() {
        whenever(guild.idLong).thenReturn(1L)
        whenever(muteRolesRepository.findById(1L)).thenReturn(Optional.of(MuteRole(1L, 2L)))
        whenever(guild.getRoleById(2L)).thenReturn(null)

        val exception = assertThrows<IllegalStateException> {
            service.planUnmute(guild, 99L, moderator, 2)
        }

        kotlin.test.assertEquals("Mute role is not configured for this server.", exception.message)
    }
}
