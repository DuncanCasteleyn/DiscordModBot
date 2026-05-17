package be.duncanc.discordmodbot.logging

import be.duncanc.discordmodbot.logging.persistence.DiscordMessage
import be.duncanc.discordmodbot.logging.persistence.LoggingSettingsRepository
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.audit.ActionType
import net.dv8tion.jda.api.audit.AuditLogEntry
import net.dv8tion.jda.api.audit.AuditLogOption
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.SelfUser
import net.dv8tion.jda.api.entities.User
import net.dv8tion.jda.api.entities.channel.unions.MessageChannelUnion
import net.dv8tion.jda.api.events.message.MessageDeleteEvent
import net.dv8tion.jda.api.requests.restaction.CacheRestAction
import net.dv8tion.jda.api.requests.restaction.pagination.AuditLogPaginationAction
import net.dv8tion.jda.api.requests.restaction.pagination.PaginationAction
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@ExtendWith(MockitoExtension::class)
class GuildLoggerTest {
    @Mock
    private lateinit var messageHistory: MessageHistory

    @Mock
    private lateinit var loggingSettingsRepository: LoggingSettingsRepository

    @Mock
    private lateinit var messageDeleteAuditStateRegistry: MessageDeleteAuditStateRegistry

    @Mock
    private lateinit var event: MessageDeleteEvent

    @Mock
    private lateinit var guild: Guild

    @Mock
    private lateinit var channel: MessageChannelUnion

    @Mock
    private lateinit var jda: JDA

    @Mock
    private lateinit var selfUser: SelfUser

    @Mock
    private lateinit var moderator: User

    @Mock
    private lateinit var auditLogEntry: AuditLogEntry

    @Mock
    private lateinit var auditLogPaginationAction: AuditLogPaginationAction

    @Mock
    private lateinit var auditLogIterator: PaginationAction.PaginationIterator<AuditLogEntry>

    @Mock
    private lateinit var userLookup: CacheRestAction<User>

    private lateinit var guildLogger: GuildLogger

    @BeforeEach
    fun setUp() {
        guildLogger = GuildLogger(messageHistory, loggingSettingsRepository, messageDeleteAuditStateRegistry)
    }

    @Test
    fun `log deleted message resolves audit state before starting user lookup`() {
        val deletedMessage = DiscordMessage(
            messageId = 100L,
            guildId = 1L,
            channelId = 10L,
            userId = 20L,
            content = "deleted content"
        )

        whenever(event.guild).thenReturn(guild)
        whenever(event.channel).thenReturn(channel)
        whenever(event.jda).thenReturn(jda)
        whenever(channel.idLong).thenReturn(10L)
        whenever(guild.idLong).thenReturn(1L)
        whenever(guild.retrieveAuditLogs()).thenReturn(auditLogPaginationAction)
        whenever(auditLogPaginationAction.cache(false)).thenReturn(auditLogPaginationAction)
        whenever(auditLogPaginationAction.limit(5)).thenReturn(auditLogPaginationAction)
        whenever(auditLogPaginationAction.iterator()).thenReturn(auditLogIterator)
        whenever(auditLogIterator.hasNext()).thenReturn(true, false)
        whenever(auditLogIterator.next()).thenReturn(auditLogEntry)
        whenever(auditLogEntry.idLong).thenReturn(42L)
        whenever(auditLogEntry.type).thenReturn(ActionType.MESSAGE_DELETE)
        whenever(auditLogEntry.targetIdLong).thenReturn(20L)
        whenever(auditLogEntry.user).thenReturn(moderator)
        whenever(auditLogEntry.getOption<String>(AuditLogOption.CHANNEL)).thenReturn("10")
        whenever(auditLogEntry.getOption<String>(AuditLogOption.COUNT)).thenReturn("1")
        whenever(messageDeleteAuditStateRegistry.get(1L, 10L, 20L)).thenReturn(null)
        whenever(jda.selfUser).thenReturn(selfUser)
        whenever(jda.retrieveUserById(20L)).thenReturn(userLookup)

        guildLogger.logDeletedMessage(event, deletedMessage, attachmentString = null)

        val stateCaptor = argumentCaptor<MessageDeleteAuditState>()
        val inOrder = inOrder(messageDeleteAuditStateRegistry, jda)
        inOrder.verify(messageDeleteAuditStateRegistry).get(1L, 10L, 20L)
        inOrder.verify(messageDeleteAuditStateRegistry).remember(eq(1L), eq(10L), eq(20L), stateCaptor.capture())
        inOrder.verify(jda).retrieveUserById(20L)
        verify(userLookup).queue(any())
        assertEquals(MessageDeleteAuditState(consumedCounts = mapOf(42L to 1)), stateCaptor.firstValue)
    }
}
