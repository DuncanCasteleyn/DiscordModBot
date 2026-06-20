package be.duncanc.discordmodbot.moderation

import be.duncanc.discordmodbot.moderation.persistence.ReportSettings
import be.duncanc.discordmodbot.moderation.persistence.ReportSettingsRepository
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.Role
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.Optional

@ExtendWith(MockitoExtension::class)
class ReportSettingsServiceTest {
    @Mock
    private lateinit var reportSettingsRepository: ReportSettingsRepository

    @Mock
    private lateinit var guild: Guild

    @Mock
    private lateinit var role: Role

    @Test
    fun `urgent mention falls back to everyone when role is not configured`() {
        val service = ReportSettingsService(reportSettingsRepository)
        whenever(guild.idLong).thenReturn(1L)
        whenever(reportSettingsRepository.findById(1L)).thenReturn(Optional.empty())

        val mention = service.getUrgentMention(guild)

        assertEquals("@everyone", mention)
    }

    @Test
    fun `urgent mention uses configured role when role still exists`() {
        val service = ReportSettingsService(reportSettingsRepository)
        whenever(guild.idLong).thenReturn(1L)
        whenever(guild.getRoleById(5L)).thenReturn(role)
        whenever(role.asMention).thenReturn("<@&5>")
        whenever(reportSettingsRepository.findById(1L)).thenReturn(Optional.of(ReportSettings(1L, urgentRoleId = 5L)))

        val mention = service.getUrgentMention(guild)

        assertEquals("<@&5>", mention)
    }

    @Test
    fun `urgent mention falls back to everyone when configured role is missing`() {
        val service = ReportSettingsService(reportSettingsRepository)
        whenever(guild.idLong).thenReturn(1L)
        whenever(guild.getRoleById(5L)).thenReturn(null)
        whenever(reportSettingsRepository.findById(1L)).thenReturn(Optional.of(ReportSettings(1L, urgentRoleId = 5L)))

        val mention = service.getUrgentMention(guild)

        assertEquals("@everyone", mention)
    }

    @Test
    fun `blocking a user creates settings and stores user id`() {
        val service = ReportSettingsService(reportSettingsRepository)
        whenever(reportSettingsRepository.findById(1L)).thenReturn(Optional.empty())

        service.blockUser(1L, 99L)

        val settingsCaptor = argumentCaptor<ReportSettings>()
        verify(reportSettingsRepository).save(settingsCaptor.capture())
        assertEquals(1L, settingsCaptor.firstValue.guildId)
        assertEquals(setOf(99L), settingsCaptor.firstValue.blockedUserIds)
    }
}
