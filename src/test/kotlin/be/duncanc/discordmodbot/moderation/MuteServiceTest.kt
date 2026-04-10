package be.duncanc.discordmodbot.moderation

import be.duncanc.discordmodbot.moderation.persistence.MuteRole
import be.duncanc.discordmodbot.moderation.persistence.MuteRolesRepository
import be.duncanc.discordmodbot.moderation.persistence.ScheduledUnmuteRepository
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.*
import java.time.OffsetDateTime

@ExtendWith(MockitoExtension::class)
class MuteServiceTest {
    @Mock
    private lateinit var muteRolesRepository: MuteRolesRepository

    @Mock
    private lateinit var scheduledUnmuteRepository: ScheduledUnmuteRepository

    @Mock
    private lateinit var scheduledUnmuteService: ScheduledUnmuteService

    private lateinit var service: MuteService

    @BeforeEach
    fun setUp() {
        service = MuteService(muteRolesRepository, scheduledUnmuteRepository, scheduledUnmuteService)
    }

    @Test
    fun `plan unmutes by role schedules a missing unmute after a year`() {
        whenever(muteRolesRepository.findAll()).thenReturn(listOf(MuteRole(1L, 2L, mutableSetOf(99L))))
        whenever(scheduledUnmuteRepository.existsByGuildIdAndUserId(1L, 99L)).thenReturn(false)
        val beforePlanning = OffsetDateTime.now()

        service.planUnmutesByRole()

        val unmuteDateTimeCaptor = argumentCaptor<OffsetDateTime>()
        verify(scheduledUnmuteService).planUnmute(eq(1L), eq(99L), unmuteDateTimeCaptor.capture())
        val afterPlanning = OffsetDateTime.now()
        kotlin.test.assertTrue(unmuteDateTimeCaptor.firstValue.isAfter(beforePlanning.plusYears(1).minusMinutes(1)))
        kotlin.test.assertTrue(unmuteDateTimeCaptor.firstValue.isBefore(afterPlanning.plusYears(1).plusMinutes(1)))
    }

    @Test
    fun `plan unmutes by role skips users that already have a scheduled unmute`() {
        whenever(muteRolesRepository.findAll()).thenReturn(listOf(MuteRole(1L, 2L, mutableSetOf(99L, 100L))))
        whenever(scheduledUnmuteRepository.existsByGuildIdAndUserId(1L, 99L)).thenReturn(true)
        whenever(scheduledUnmuteRepository.existsByGuildIdAndUserId(1L, 100L)).thenReturn(false)

        service.planUnmutesByRole()

        verify(scheduledUnmuteRepository).existsByGuildIdAndUserId(1L, 99L)
        verify(scheduledUnmuteRepository).existsByGuildIdAndUserId(1L, 100L)
        verify(scheduledUnmuteService).planUnmute(eq(1L), eq(100L), any())
        verify(scheduledUnmuteService, never()).planUnmute(eq(1L), eq(99L), any())
    }

    @Test
    fun `plan unmutes by role does nothing when there are no muted users`() {
        whenever(muteRolesRepository.findAll()).thenReturn(listOf(MuteRole(1L, 2L)))

        service.planUnmutesByRole()

        verifyNoInteractions(scheduledUnmuteRepository, scheduledUnmuteService)
    }
}
