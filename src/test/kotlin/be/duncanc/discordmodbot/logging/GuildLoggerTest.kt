package be.duncanc.discordmodbot.logging

import be.duncanc.discordmodbot.logging.persistence.DiscordMessage
import be.duncanc.discordmodbot.logging.persistence.LoggingSettings
import be.duncanc.discordmodbot.logging.persistence.LoggingSettingsRepository
import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.audit.AuditLogEntry
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.SelfMember
import net.dv8tion.jda.api.entities.User
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel
import net.dv8tion.jda.api.entities.channel.unions.GuildMessageChannelUnion
import net.dv8tion.jda.api.entities.channel.unions.MessageChannelUnion
import net.dv8tion.jda.api.events.message.MessageBulkDeleteEvent
import net.dv8tion.jda.api.events.message.MessageDeleteEvent
import net.dv8tion.jda.api.requests.restaction.CacheRestAction
import net.dv8tion.jda.api.requests.restaction.MessageCreateAction
import net.dv8tion.jda.api.requests.restaction.pagination.AuditLogPaginationAction
import net.dv8tion.jda.api.requests.restaction.pagination.PaginationAction
import net.dv8tion.jda.api.utils.FileUpload
import net.dv8tion.jda.api.utils.messages.MessageCreateData
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doAnswer
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.timeout
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.Optional
import java.util.function.Consumer

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
    private lateinit var channel: MessageChannelUnion

    @Mock
    private lateinit var bulkChannel: GuildMessageChannelUnion

    @Mock
    private lateinit var selfMember: SelfMember

    @Mock
    private lateinit var event: MessageDeleteEvent

    @Mock
    private lateinit var bulkDeleteEvent: MessageBulkDeleteEvent

    @Mock
    private lateinit var jda: JDA

    @Mock
    private lateinit var user: User

    @Mock
    private lateinit var auditLogs: AuditLogPaginationAction

    @Mock
    private lateinit var auditLogIterator: PaginationAction.PaginationIterator<AuditLogEntry>

    @Mock
    private lateinit var retrieveUserAction: CacheRestAction<User>

    @Mock
    private lateinit var logChannel: TextChannel

    @Mock
    private lateinit var sendAction: MessageCreateAction

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

    @Test
    fun `deleted message log includes the replied to URL`() {
        val guildLogger = guildLogger()
        val oldMessage = DiscordMessage(
            messageId = 100L,
            guildId = 1L,
            channelId = 10L,
            userId = 20L,
            content = "deleted content",
            repliedToUrl = "https://discord.com/channels/1/10/50"
        )
        whenever(event.guild).thenReturn(guild)
        whenever(event.channel).thenReturn(channel)
        whenever(event.jda).thenReturn(jda)
        whenever(guild.idLong).thenReturn(1L)
        whenever(channel.idLong).thenReturn(10L)
        whenever(channel.name).thenReturn("general")
        whenever(guild.retrieveAuditLogs()).thenReturn(auditLogs)
        whenever(auditLogs.cache(false)).thenReturn(auditLogs)
        whenever(auditLogs.limit(5)).thenReturn(auditLogs)
        whenever(auditLogs.iterator()).thenReturn(auditLogIterator)
        whenever(auditLogIterator.hasNext()).thenReturn(false)
        whenever(jda.retrieveUserById(20L)).thenReturn(retrieveUserAction)
        whenever(user.name).thenReturn("user")
        doAnswer { invocation ->
            invocation.component1<Consumer<User>>().accept(user)
            null
        }.whenever(retrieveUserAction).queue(any())
        whenever(loggingSettingsRepository.findById(1L)).thenReturn(
            Optional.of(LoggingSettings(1L, userLogChannel = 30L))
        )
        whenever(guild.getTextChannelById(30L)).thenReturn(logChannel)
        whenever(logChannel.sendMessage(any<MessageCreateData>())).thenReturn(sendAction)

        guildLogger.logDeletedMessage(event, oldMessage, null)

        val messageCaptor = argumentCaptor<MessageCreateData>()
        verify(logChannel).sendMessage(messageCaptor.capture())
        val repliedToField = messageCaptor.firstValue.embeds.single().fields.first { it.name == "Replied to" }
        assertEquals("[Link](https://discord.com/channels/1/10/50)", repliedToField.value)
    }

    @Test
    fun `bulk deleted message log includes the replied to URL`() {
        val guildLogger = guildLogger()
        val oldMessage = DiscordMessage(
            messageId = 100L,
            guildId = 1L,
            channelId = 10L,
            userId = 20L,
            content = "deleted content",
            repliedToUrl = "https://discord.com/channels/1/10/50"
        )
        whenever(bulkDeleteEvent.guild).thenReturn(guild)
        whenever(bulkDeleteEvent.channel).thenReturn(bulkChannel)
        whenever(bulkDeleteEvent.jda).thenReturn(jda)
        whenever(bulkDeleteEvent.messageIds).thenReturn(listOf("100"))
        whenever(guild.idLong).thenReturn(1L)
        whenever(bulkChannel.idLong).thenReturn(10L)
        whenever(bulkChannel.name).thenReturn("general")
        whenever(messageHistory.getMessage(10L, 100L)).thenReturn(oldMessage)
        whenever(jda.retrieveUserById(20L)).thenReturn(retrieveUserAction)
        doAnswer { invocation ->
            invocation.component1<Consumer<User>>().accept(user)
            null
        }.whenever(retrieveUserAction).queue(any(), any())
        whenever(loggingSettingsRepository.findById(1L)).thenReturn(
            Optional.of(LoggingSettings(1L, userLogChannel = 30L))
        )
        whenever(guild.getTextChannelById(30L)).thenReturn(logChannel)
        whenever(logChannel.sendFiles(any<FileUpload>())).thenReturn(sendAction)

        guildLogger.onMessageBulkDelete(bulkDeleteEvent)

        val fileCaptor = argumentCaptor<FileUpload>()
        verify(logChannel, timeout(1000)).sendFiles(fileCaptor.capture())
        val logContent = fileCaptor.firstValue.data.use { it.readBytes().toString(Charsets.UTF_8) }
        assertTrue(logContent.contains("Replied to:\nhttps://discord.com/channels/1/10/50"))
    }

    @Test
    fun `bulk deleted message log is produced when author lookup fails`() {
        val guildLogger = guildLogger()
        val oldMessage = DiscordMessage(
            messageId = 100L,
            guildId = 1L,
            channelId = 10L,
            userId = 20L,
            content = "deleted content"
        )
        whenever(bulkDeleteEvent.guild).thenReturn(guild)
        whenever(bulkDeleteEvent.channel).thenReturn(bulkChannel)
        whenever(bulkDeleteEvent.jda).thenReturn(jda)
        whenever(bulkDeleteEvent.messageIds).thenReturn(listOf("100"))
        whenever(guild.idLong).thenReturn(1L)
        whenever(bulkChannel.idLong).thenReturn(10L)
        whenever(bulkChannel.name).thenReturn("general")
        whenever(messageHistory.getMessage(10L, 100L)).thenReturn(oldMessage)
        whenever(jda.retrieveUserById(20L)).thenReturn(retrieveUserAction)
        doAnswer { invocation ->
            invocation.component2<Consumer<Throwable>>().accept(RuntimeException("lookup failed"))
            null
        }.whenever(retrieveUserAction).queue(any(), any())
        whenever(loggingSettingsRepository.findById(1L)).thenReturn(
            Optional.of(LoggingSettings(1L, userLogChannel = 30L))
        )
        whenever(guild.getTextChannelById(30L)).thenReturn(logChannel)
        whenever(logChannel.sendFiles(any<FileUpload>())).thenReturn(sendAction)

        guildLogger.onMessageBulkDelete(bulkDeleteEvent)

        val fileCaptor = argumentCaptor<FileUpload>()
        verify(logChannel, timeout(1000)).sendFiles(fileCaptor.capture())
        val logContent = fileCaptor.firstValue.data.use { it.readBytes().toString(Charsets.UTF_8) }
        assertTrue(logContent.contains("20:\ndeleted content"))
    }

    private fun guildLogger(): GuildLogger {
        return GuildLogger(
            messageHistory,
            loggingSettingsRepository,
            messageDeleteAuditStateRegistry
        )
    }
}
