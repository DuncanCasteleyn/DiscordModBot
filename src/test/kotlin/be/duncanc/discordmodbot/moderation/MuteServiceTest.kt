package be.duncanc.discordmodbot.moderation

import be.duncanc.discordmodbot.moderation.persistence.MuteRole
import be.duncanc.discordmodbot.moderation.persistence.MuteRolesRepository
import jakarta.persistence.EntityExistsException
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import java.time.OffsetDateTime

@ExtendWith(MockitoExtension::class)
class MuteServiceTest {
    @Mock
    private lateinit var muteRolesRepository: MuteRolesRepository

    @Mock
    private lateinit var scheduledUnmuteService: ScheduledUnmuteService

    private lateinit var service: MuteService

    @BeforeEach
    fun setUp() {
        service = MuteService(muteRolesRepository, scheduledUnmuteService)
    }

    @Test
    fun `plan unmutes by role schedules a missing unmute after a year`() {
        whenever(muteRolesRepository.findAll()).thenReturn(listOf(MuteRole(1L, 2L, mutableSetOf(99L))))
        val beforePlanning = OffsetDateTime.now()

        service.planUnmutesByRole()

        val unmuteDateTimeCaptor = argumentCaptor<OffsetDateTime>()
        verify(scheduledUnmuteService).planDefaultUnmute(eq(1L), eq(99L), unmuteDateTimeCaptor.capture())
        val afterPlanning = OffsetDateTime.now()
        kotlin.test.assertTrue(unmuteDateTimeCaptor.firstValue.isAfter(beforePlanning.plusYears(1).minusMinutes(1)))
        kotlin.test.assertTrue(unmuteDateTimeCaptor.firstValue.isBefore(afterPlanning.plusYears(1).plusMinutes(1)))
    }

    @Test
    fun `plan unmutes by role skips users whose default unmute already exists`() {
        whenever(muteRolesRepository.findAll()).thenReturn(listOf(MuteRole(1L, 2L, mutableSetOf(99L, 100L))))
        doThrow(EntityExistsException()).whenever(scheduledUnmuteService)
            .planDefaultUnmute(eq(1L), eq(99L), any<OffsetDateTime>())

        service.planUnmutesByRole()

        verify(scheduledUnmuteService).planDefaultUnmute(eq(1L), eq(100L), any<OffsetDateTime>())
        verify(scheduledUnmuteService).planDefaultUnmute(eq(1L), eq(99L), any<OffsetDateTime>())
    }

    @Test
    fun `plan unmutes by role does nothing when there are no muted users`() {
        whenever(muteRolesRepository.findAll()).thenReturn(listOf(MuteRole(1L, 2L)))

        service.planUnmutesByRole()

        verifyNoInteractions(scheduledUnmuteService)
    }
}
