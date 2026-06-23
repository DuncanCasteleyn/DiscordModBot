package be.duncanc.discordmodbot.logging

import be.duncanc.discordmodbot.logging.persistence.LoggingSettings
import be.duncanc.discordmodbot.logging.persistence.LoggingSettingsRepository
import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.SelfMember
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.whenever
import java.util.Optional

@ExtendWith(MockitoExtension::class)
class GuildLoggerTest {
    @Mock
    private lateinit var messageHistory: MessageHistory

    @Mock
    private lateinit var loggingSettingsRepository: LoggingSettingsRepository

    @Mock
    private lateinit var messageDeleteAuditStateRegistry: MessageDeleteAuditStateRegistry

    @Mock
    private lateinit var guild: Guild

    @Mock
    private lateinit var textChannel: TextChannel

    @Mock
    private lateinit var selfMember: SelfMember

    @Test
    fun `canSendModeratorLog returns false when no logging settings exist`() {
        val guildLogger = guildLogger()
        whenever(guild.idLong).thenReturn(1L)
        whenever(loggingSettingsRepository.findById(1L)).thenReturn(Optional.empty())

        val canSend = guildLogger.canSendModeratorLog(guild)

        assertFalse(canSend)
    }

    @Test
    fun `canSendModeratorLog returns false when moderator log channel is not configured`() {
        val guildLogger = guildLogger()
        whenever(guild.idLong).thenReturn(1L)
        whenever(loggingSettingsRepository.findById(1L))
            .thenReturn(Optional.of(LoggingSettings(1L, modLogChannel = null)))

        val canSend = guildLogger.canSendModeratorLog(guild)

        assertFalse(canSend)
    }

    @Test
    fun `canSendModeratorLog returns false when configured channel does not exist`() {
        val guildLogger = guildLogger()
        whenever(guild.idLong).thenReturn(1L)
        whenever(loggingSettingsRepository.findById(1L))
            .thenReturn(Optional.of(LoggingSettings(1L, modLogChannel = 123L)))
        whenever(guild.getTextChannelById(123L)).thenReturn(null)

        val canSend = guildLogger.canSendModeratorLog(guild)

        assertFalse(canSend)
    }

    @Test
    fun `canSendModeratorLog returns false when bot lacks required permissions`() {
        val guildLogger = guildLogger()
        whenever(guild.idLong).thenReturn(1L)
        whenever(loggingSettingsRepository.findById(1L))
            .thenReturn(Optional.of(LoggingSettings(1L, modLogChannel = 123L)))
        whenever(guild.getTextChannelById(123L)).thenReturn(textChannel)
        whenever(guild.selfMember).thenReturn(selfMember)
        whenever(
            selfMember.hasPermission(
                textChannel,
                Permission.VIEW_CHANNEL,
                Permission.MESSAGE_SEND,
                Permission.MESSAGE_EMBED_LINKS
            )
        ).thenReturn(false)

        val canSend = guildLogger.canSendModeratorLog(guild)

        assertFalse(canSend)
    }

    @Test
    fun `canSendModeratorLog returns true when channel exists and bot has permissions`() {
        val guildLogger = guildLogger()
        whenever(guild.idLong).thenReturn(1L)
        whenever(loggingSettingsRepository.findById(1L))
            .thenReturn(Optional.of(LoggingSettings(1L, modLogChannel = 123L)))
        whenever(guild.getTextChannelById(123L)).thenReturn(textChannel)
        whenever(guild.selfMember).thenReturn(selfMember)
        whenever(
            selfMember.hasPermission(
                textChannel,
                Permission.VIEW_CHANNEL,
                Permission.MESSAGE_SEND,
                Permission.MESSAGE_EMBED_LINKS
            )
        ).thenReturn(true)

        val canSend = guildLogger.canSendModeratorLog(guild)

        assertTrue(canSend)
    }

    private fun guildLogger(): GuildLogger {
        return GuildLogger(
            messageHistory,
            loggingSettingsRepository,
            messageDeleteAuditStateRegistry
        )
    }
}
