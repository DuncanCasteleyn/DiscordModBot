package be.duncanc.discordmodbot.moderation

import be.duncanc.discordmodbot.logging.GuildLogger
import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.Member
import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.entities.MessageEmbed
import net.dv8tion.jda.api.entities.User
import net.dv8tion.jda.api.entities.channel.unions.GuildMessageChannelUnion
import net.dv8tion.jda.api.events.interaction.command.MessageContextInteractionEvent
import net.dv8tion.jda.api.interactions.commands.Command
import net.dv8tion.jda.api.requests.restaction.interactions.ReplyCallbackAction
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@ExtendWith(MockitoExtension::class)
class ReportMessageContextMenuTest {
    @Mock
    private lateinit var guildLogger: GuildLogger

    @Mock
    private lateinit var muteService: MuteService

    @Mock
    private lateinit var reportSettingsService: ReportSettingsService

    @Mock
    private lateinit var reportRateLimitService: ReportRateLimitService

    @Mock
    private lateinit var event: MessageContextInteractionEvent

    @Mock
    private lateinit var guild: Guild

    @Mock
    private lateinit var reporter: Member

    @Mock
    private lateinit var reporterUser: User

    @Mock
    private lateinit var targetMember: Member

    @Mock
    private lateinit var targetUser: User

    @Mock
    private lateinit var targetMessage: Message

    @Mock
    private lateinit var channel: GuildMessageChannelUnion

    @Mock
    private lateinit var attachment: Message.Attachment

    @Mock
    private lateinit var replyAction: ReplyCallbackAction

    private lateinit var command: ReportMessageContextMenu

    @BeforeEach
    fun setUp() {
        command = ReportMessageContextMenu(guildLogger, muteService, reportSettingsService, reportRateLimitService)
    }

    @Test
    fun `non-matching message context menu returns early`() {
        whenever(event.name).thenReturn("Other Command")

        command.onMessageContextInteraction(event)

        verify(guildLogger, never()).logWithContent(
            any<EmbedBuilder>(),
            any<User>(),
            any<Guild>(),
            isNull<List<MessageEmbed>>(),
            any<GuildLogger.LogTypeAction>(),
            any<String>()
        )
        verify(event, never()).reply(any<String>())
    }

    @Test
    fun `non-urgent report logs to moderation channel with here ping`() {
        stubReport("Report Message")

        command.onMessageContextInteraction(event)

        val embedCaptor = argumentCaptor<EmbedBuilder>()
        verify(guildLogger).logWithContent(
            embedCaptor.capture(),
            eq(targetUser),
            eq(guild),
            isNull<List<MessageEmbed>>(),
            eq(GuildLogger.LogTypeAction.MODERATOR),
            eq("@here")
        )
        val embed = embedCaptor.firstValue.build()
        assertEquals("Message report", embed.title)
        assertEquals("Reporter", embed.fields[0].name)
        assertEquals("Duncan(reporter-user)", embed.fields[0].value)
        assertEquals("Reported user", embed.fields[1].name)
        assertEquals("ReportedUser", embed.fields[1].value)
        assertTrue(embed.fields.any { it.name == "Message" && it.value == "This should be reviewed" })
        assertTrue(embed.fields.any { it.name == "Attachment(s)" && it.value == "https://example.invalid/image.png" })
        verify(event).reply("Your report has been sent to the moderation team.")
    }

    @Test
    fun `urgent report logs to moderation channel with everyone ping`() {
        stubReport("Urgent Report Message")
        whenever(reportSettingsService.getUrgentMention(guild)).thenReturn("@everyone")

        command.onMessageContextInteraction(event)

        val embedCaptor = argumentCaptor<EmbedBuilder>()
        verify(guildLogger).logWithContent(
            embedCaptor.capture(),
            eq(targetUser),
            eq(guild),
            isNull<List<MessageEmbed>>(),
            eq(GuildLogger.LogTypeAction.MODERATOR),
            eq("@everyone")
        )
        assertEquals("Urgent message report", embedCaptor.firstValue.build().title)
        verify(event).reply("Your report has been sent to the moderation team.")
    }

    @Test
    fun `urgent report logs to moderation channel with configured role ping`() {
        stubReport("Urgent Report Message")
        whenever(reportSettingsService.getUrgentMention(guild)).thenReturn("<@&5>")

        command.onMessageContextInteraction(event)

        verify(guildLogger).logWithContent(
            any<EmbedBuilder>(),
            eq(targetUser),
            eq(guild),
            isNull<List<MessageEmbed>>(),
            eq(GuildLogger.LogTypeAction.MODERATOR),
            eq("<@&5>")
        )
        verify(event).reply("Your report has been sent to the moderation team.")
    }

    @Test
    fun `rate limited member cannot report messages`() {
        stubRateLimitedReporterContext("Report Message")

        command.onMessageContextInteraction(event)

        verify(event).reply("You can only report one message every 5 minutes.")
        verify(guildLogger, never()).logWithContent(
            any<EmbedBuilder>(),
            any<User>(),
            any<Guild>(),
            isNull<List<MessageEmbed>>(),
            any<GuildLogger.LogTypeAction>(),
            any<String>()
        )
    }

    @Test
    fun `timed out member cannot report messages`() {
        stubBlockedReporterContext("Report Message", timedOut = true)

        command.onMessageContextInteraction(event)

        verify(event).reply("You cannot report messages while timed out or muted.")
        verify(reportRateLimitService, never()).tryConsume(any(), any())
        verify(guildLogger, never()).logWithContent(
            any<EmbedBuilder>(),
            any<User>(),
            any<Guild>(),
            isNull<List<MessageEmbed>>(),
            any<GuildLogger.LogTypeAction>(),
            any<String>()
        )
    }

    @Test
    fun `muted member cannot report messages`() {
        stubBlockedReporterContext("Urgent Report Message", muted = true)

        command.onMessageContextInteraction(event)

        verify(event).reply("You cannot report messages while timed out or muted.")
        verify(reportRateLimitService, never()).tryConsume(any(), any())
        verify(guildLogger, never()).logWithContent(
            any<EmbedBuilder>(),
            any<User>(),
            any<Guild>(),
            isNull<List<MessageEmbed>>(),
            any<GuildLogger.LogTypeAction>(),
            any<String>()
        )
    }

    @Test
    fun `blocked member cannot report messages`() {
        stubBlockedReporterContext("Report Message", blocked = true)

        command.onMessageContextInteraction(event)

        verify(event).reply("You are not allowed to report messages in this server.")
        verify(reportRateLimitService, never()).tryConsume(any(), any())
        verify(guildLogger, never()).logWithContent(
            any<EmbedBuilder>(),
            any<User>(),
            any<Guild>(),
            isNull<List<MessageEmbed>>(),
            any<GuildLogger.LogTypeAction>(),
            any<String>()
        )
    }

    @Test
    fun `command data includes urgent and non-urgent message context menus`() {
        val commands = command.getCommandsData()

        assertEquals(2, commands.size)
        assertEquals(listOf(Command.Type.MESSAGE, Command.Type.MESSAGE), commands.map { it.type })
        assertEquals(listOf("Report Message", "Urgent Report Message"), commands.map { it.name })
    }

    private fun stubReport(commandName: String, timedOut: Boolean = false, muted: Boolean = false) {
        stubReporterContext(commandName, timedOut, muted)
        stubTargetMessage()
    }

    private fun stubBlockedReporterContext(
        commandName: String,
        timedOut: Boolean = false,
        muted: Boolean = false,
        blocked: Boolean = false
    ) {
        whenever(event.name).thenReturn(commandName)
        whenever(event.guild).thenReturn(guild)
        whenever(event.member).thenReturn(reporter)
        whenever(event.reply(any<String>())).thenReturn(replyAction)
        whenever(replyAction.setEphemeral(true)).thenReturn(replyAction)
        whenever(reporter.isTimedOut).thenReturn(timedOut)
        if (!timedOut) {
            whenever(guild.idLong).thenReturn(1L)
            whenever(reporter.idLong).thenReturn(99L)
            whenever(muteService.isUserMuted(1L, 99L)).thenReturn(muted)
            if (!muted) {
                whenever(reportSettingsService.isUserBlocked(1L, 99L)).thenReturn(blocked)
            }
        }
    }

    private fun stubRateLimitedReporterContext(commandName: String) {
        whenever(event.name).thenReturn(commandName)
        whenever(event.guild).thenReturn(guild)
        whenever(event.member).thenReturn(reporter)
        whenever(event.reply(any<String>())).thenReturn(replyAction)
        whenever(replyAction.setEphemeral(true)).thenReturn(replyAction)
        whenever(guild.idLong).thenReturn(1L)
        whenever(reporter.idLong).thenReturn(99L)
        whenever(reporter.isTimedOut).thenReturn(false)
        whenever(muteService.isUserMuted(1L, 99L)).thenReturn(false)
        whenever(reportSettingsService.isUserBlocked(1L, 99L)).thenReturn(false)
        whenever(reportRateLimitService.tryConsume(1L, 99L)).thenReturn(false)
        whenever(reportRateLimitService.rateLimitDescription()).thenReturn("5 minutes")
    }

    private fun stubReporterContext(commandName: String, timedOut: Boolean = false, muted: Boolean = false) {
        whenever(event.name).thenReturn(commandName)
        whenever(event.guild).thenReturn(guild)
        whenever(event.member).thenReturn(reporter)
        whenever(event.reply(any<String>())).thenReturn(replyAction)
        whenever(replyAction.setEphemeral(true)).thenReturn(replyAction)
        whenever(guild.idLong).thenReturn(1L)
        whenever(reporter.idLong).thenReturn(99L)
        whenever(reporter.isTimedOut).thenReturn(timedOut)
        if (!timedOut) {
            whenever(muteService.isUserMuted(1L, 99L)).thenReturn(muted)
            if (!muted) {
                whenever(reportSettingsService.isUserBlocked(1L, 99L)).thenReturn(false)
                whenever(reportRateLimitService.tryConsume(1L, 99L)).thenReturn(true)
            }
        }
        whenever(reporter.user).thenReturn(reporterUser)
        whenever(reporter.nickname).thenReturn("Duncan")
        whenever(reporterUser.name).thenReturn("reporter-user")
    }

    private fun stubTargetMessage() {
        whenever(event.target).thenReturn(targetMessage)
        whenever(targetMessage.author).thenReturn(targetUser)
        whenever(targetMessage.member).thenReturn(targetMember)
        whenever(targetMember.nickname).thenReturn(null)
        whenever(targetMember.user).thenReturn(targetUser)
        whenever(targetUser.name).thenReturn("ReportedUser")
        whenever(targetMessage.guildChannel).thenReturn(channel)
        whenever(channel.asMention).thenReturn("<#123>")
        whenever(targetMessage.jumpUrl).thenReturn("https://discord.com/channels/1/123/456")
        whenever(targetMessage.contentRaw).thenReturn("This should be reviewed")
        whenever(targetMessage.attachments).thenReturn(listOf(attachment))
        whenever(attachment.url).thenReturn("https://example.invalid/image.png")
    }
}
