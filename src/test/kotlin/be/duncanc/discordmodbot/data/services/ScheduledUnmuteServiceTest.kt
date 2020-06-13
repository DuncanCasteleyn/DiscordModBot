package be.duncanc.discordmodbot.data.services

import be.duncanc.discordmodbot.bot.RunBots
import be.duncanc.discordmodbot.data.entities.MuteRole
import be.duncanc.discordmodbot.data.entities.ScheduledUnmute
import be.duncanc.discordmodbot.data.repositories.MuteRolesRepository
import be.duncanc.discordmodbot.data.repositories.ScheduledUnmuteRepository
import com.nhaarman.mockitokotlin2.*
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.Member
import net.dv8tion.jda.api.entities.Role
import net.dv8tion.jda.api.requests.restaction.AuditableRestAction
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.boot.test.mock.mockito.SpyBean
import java.time.OffsetDateTime
import java.util.*

@SpringBootTest(classes = [ScheduledUnmuteService::class])
@ExtendWith(MockitoExtension::class)
internal class ScheduledUnmuteServiceTest {
    @MockBean
    lateinit var scheduledUnmuteRepository: ScheduledUnmuteRepository

    @MockBean
    lateinit var muteRolesRepository: MuteRolesRepository

    @MockBean
    lateinit var runBots: RunBots

    @Mock
    lateinit var jda: JDA

    @Mock
    lateinit var guild: Guild

    @Mock
    lateinit var role: Role

    @Mock
    lateinit var auditableRestAction: AuditableRestAction<Void>

    @SpyBean
    lateinit var scheduledUnmuteService: ScheduledUnmuteService

    @AfterEach
    fun `verify no more interactions with any mock or spy`() {
        verifyNoMoreInteractions(scheduledUnmuteRepository, muteRolesRepository, runBots, scheduledUnmuteService, jda, guild, role)
    }

    @Test
    fun `planning an unmute should fail when using a time in the future less than 30 minutes from now`() {
        val unmuteDateTime = OffsetDateTime.now().plusMinutes(29)
        // Act
        val illegalArgumentException = assertThrows<IllegalArgumentException> {
            scheduledUnmuteService.planUnmute(0, 0, unmuteDateTime)
        }
        assertEquals("An unmute should be planned at least more than 30 minutes in the future", illegalArgumentException.message)
        verify(scheduledUnmuteService).planUnmute(0, 0, unmuteDateTime)
    }

    @Test
    fun `planning an unmute should fail when using time in the past`() {
        val unmuteDateTime = OffsetDateTime.now().minusNanos(1)
        // Act
        val illegalArgumentException = assertThrows<IllegalArgumentException> {
            scheduledUnmuteService.planUnmute(0, 0, unmuteDateTime)
        }
        assertEquals("An unmute should not be planned in the past", illegalArgumentException.message)
        verify(scheduledUnmuteService).planUnmute(0, 0, unmuteDateTime)
    }

    @Test
    fun `planning an unmute should fail when it takes longer than 1 year`() {
        val unmuteDateTime = OffsetDateTime.now().plusYears(1).plusMinutes(30)

        val illegalArgumentException = assertThrows<IllegalArgumentException> {
            scheduledUnmuteService.planUnmute(0, 0, unmuteDateTime)
        }
        assertEquals("A mute can't take longer than 1 year", illegalArgumentException.message)
        verify(scheduledUnmuteService).planUnmute(0, 0, unmuteDateTime)
    }

    @Test
    fun `planning an unmute should pass when using a time in the future`() {
        val unmuteDateTime = OffsetDateTime.now().plusHours(1)
        scheduledUnmuteService.planUnmute(0, 0, unmuteDateTime)

        verify(scheduledUnmuteService).planUnmute(0, 0, unmuteDateTime)
        @Suppress("RemoveExplicitTypeArguments")
        verify(scheduledUnmuteRepository).save(any<ScheduledUnmute>())
    }

    @Test
    fun `Performing unmute should work`() {
        // Arrange
        val scheduledUnmute = ScheduledUnmute(1, 1, OffsetDateTime.MIN)
        whenever(scheduledUnmuteRepository.findByUnmuteDateTimeAfter(any())).thenReturn(Collections.singleton(scheduledUnmute))
        whenever(runBots.runningBots).thenReturn(Collections.singletonList(jda))
        val muteRole = Optional.of(MuteRole(1, 1))
        whenever(muteRolesRepository.findById(any())).thenReturn(muteRole)
        whenever(jda.getGuildById(1)).thenReturn(guild)
        whenever(guild.getRoleById(1)).thenReturn(role)
        whenever(guild.removeRoleFromMember(1, role)).thenReturn(auditableRestAction)
        // Act
        scheduledUnmuteService.performUnmute()
        // Verify
        verify(scheduledUnmuteService).performUnmute()
        verify(scheduledUnmuteRepository).findByUnmuteDateTimeAfter(any())
        verify(jda).getGuildById(1)
        verify(runBots).runningBots
        verify(guild).idLong
        verify(muteRolesRepository).findById(any())
        verify(guild).getRoleById(1)
        verify(guild).removeRoleFromMember(eq<Long>(1), any())
    }
}
