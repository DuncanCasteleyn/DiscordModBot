package be.duncanc.discordmodbot.data.services

import be.duncanc.discordmodbot.bot.RunBots
import be.duncanc.discordmodbot.data.entities.ScheduledUnmute
import be.duncanc.discordmodbot.data.repositories.MuteRolesRepository
import be.duncanc.discordmodbot.data.repositories.ScheduledUnmuteRepository
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.Mockito.*
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.boot.test.mock.mockito.SpyBean
import java.time.OffsetDateTime

@SpringBootTest(classes = [ScheduledUnmuteService::class])
internal class ScheduledUnmuteServiceTest {
    @MockBean
    lateinit var unmuteRepository: ScheduledUnmuteRepository

    @MockBean
    lateinit var muteRolesRepository: MuteRolesRepository

    @MockBean
    lateinit var runBots: RunBots

    @SpyBean
    lateinit var scheduledUnmuteService: ScheduledUnmuteService

    @AfterEach
    fun `verify no more interactions with any mock or spy`() {
        verifyNoMoreInteractions(unmuteRepository, muteRolesRepository, runBots, scheduledUnmuteService)
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
        verify(unmuteRepository).save(any(ScheduledUnmute::class.java))
    }
}
