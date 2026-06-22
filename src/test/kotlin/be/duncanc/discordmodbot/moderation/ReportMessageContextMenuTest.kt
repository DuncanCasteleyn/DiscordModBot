package be.duncanc.discordmodbot.moderation

import be.duncanc.discordmodbot.logging.GuildLogger
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.components.actionrow.ActionRow
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.Member
import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.entities.MessageEmbed
import net.dv8tion.jda.api.entities.User
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel
import net.dv8tion.jda.api.entities.channel.unions.GuildMessageChannelUnion
import net.dv8tion.jda.api.events.interaction.command.MessageContextInteractionEvent
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent
import net.dv8tion.jda.api.interactions.InteractionHook
import net.dv8tion.jda.api.interactions.commands.Command
import net.dv8tion.jda.api.requests.RestAction
import net.dv8tion.jda.api.requests.restaction.WebhookMessageEditAction
import net.dv8tion.jda.api.requests.restaction.interactions.MessageEditCallbackAction
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
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.function.Consumer

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
    private lateinit var reportedMessageService: ReportedMessageService

    @Mock
    private lateinit var event: MessageContextInteractionEvent

    @Mock
    private lateinit var buttonEvent: ButtonInteractionEvent

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

    @Mock
    private lateinit var editAction: MessageEditCallbackAction

    @Mock
    private lateinit var deferredEditAction: MessageEditCallbackAction

    @Mock
    private lateinit var interactionHook: InteractionHook

    @Mock
    private lateinit var hookEditAction: WebhookMessageEditAction<Message>

    @Mock
    private lateinit var jda: JDA

    @Mock
    private lateinit var messageChannel: MessageChannel

    @Mock
    private lateinit var retrieveMessageAction: RestAction<Message>

    private lateinit var command: ReportMessageContextMenu

    @BeforeEach
    fun setUp() {
        command = ReportMessageContextMenu(
            guildLogger,
            muteService,
            reportSettingsService,
            reportRateLimitService,
            reportedMessageService
        )
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
        assertEquals("Duncan(reporter-user) (99)", embed.fields[0].value)
        assertEquals("Reported user", embed.fields[1].name)
        assertEquals("ReportedUser", embed.fields[1].value)
        assertTrue(embed.fields.any { it.name == "Message" && it.value == "This should be reviewed" })
        assertTrue(embed.fields.any { it.name == "Attachment(s)" && it.value == "https://example.invalid/image.png" })
        verify(reportedMessageService).markReported(1L, 123L, 456L, urgent = false)
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
        verify(reportedMessageService).markReported(1L, 123L, 456L, urgent = true)
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
        verify(reportedMessageService).markReported(1L, 123L, 456L, urgent = true)
        verify(event).reply("Your report has been sent to the moderation team.")
    }

    @Test
    fun `non urgent duplicate report is blocked`() {
        stubReporterAndTargetIds("Report Message")
        whenever(reportedMessageService.getState(1L, 123L, 456L)).thenReturn(ReportedMessageState.NON_URGENT)

        command.onMessageContextInteraction(event)

        verify(event).reply("This issue was already reported.")
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
    fun `urgent report over non urgent report asks for confirmation`() {
        stubReporterAndTargetIds("Urgent Report Message")
        whenever(reportedMessageService.getState(1L, 123L, 456L)).thenReturn(ReportedMessageState.NON_URGENT)
        whenever(replyAction.addComponents(any<ActionRow>())).thenReturn(replyAction)

        command.onMessageContextInteraction(event)

        verify(event).reply("This message was already reported as non-urgent. Confirm if you want to report it as urgent.")
        verify(replyAction).addComponents(any<ActionRow>())
        verify(reportRateLimitService, never()).tryConsume(any(), any())
    }

    @Test
    fun `urgent duplicate report is blocked`() {
        stubReporterAndTargetIds("Urgent Report Message")
        whenever(reportedMessageService.getState(1L, 123L, 456L)).thenReturn(ReportedMessageState.URGENT)

        command.onMessageContextInteraction(event)

        verify(event).reply("This issue was already reported.")
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
    fun `urgent confirmation by another user is rejected`() {
        whenever(buttonEvent.componentId).thenReturn("report:urgent:1:123:456:99")
        whenever(buttonEvent.user).thenReturn(reporterUser)
        whenever(reporterUser.idLong).thenReturn(100L)
        whenever(buttonEvent.reply(any<String>())).thenReturn(replyAction)
        whenever(replyAction.setEphemeral(true)).thenReturn(replyAction)

        command.onButtonInteraction(buttonEvent)

        verify(buttonEvent).reply("This confirmation is only for the user who requested it.")
    }

    @Test
    fun `urgent confirmation logs urgent report and upgrades state`() {
        stubUrgentConfirmation()
        whenever(reportedMessageService.getState(1L, 123L, 456L)).thenReturn(ReportedMessageState.NON_URGENT)
        whenever(reportRateLimitService.tryConsume(1L, 99L)).thenReturn(true)
        whenever(buttonEvent.jda).thenReturn(jda)
        whenever(jda.getChannelById(MessageChannel::class.java, 123L)).thenReturn(messageChannel)
        whenever(messageChannel.retrieveMessageById(456L)).thenReturn(retrieveMessageAction)
        doRetrieveMessageSuccess()
        whenever(reportSettingsService.getUrgentMention(guild)).thenReturn("@everyone")

        command.onButtonInteraction(buttonEvent)

        verify(guildLogger).logWithContent(
            any<EmbedBuilder>(),
            eq(targetUser),
            eq(guild),
            isNull<List<MessageEmbed>>(),
            eq(GuildLogger.LogTypeAction.MODERATOR),
            eq("@everyone")
        )
        verify(reportedMessageService).markUrgent(1L, 123L, 456L)
        verify(buttonEvent).deferEdit()
        verify(interactionHook).editOriginal("Your urgent report has been sent to the moderation team.")
        verify(hookEditAction).setComponents(emptyList<ActionRow>())
    }

    @Test
    fun `urgent confirmation updates deferred response when message retrieval fails`() {
        stubUrgentConfirmation(includeRetrievedMessageDetails = false)
        whenever(reportedMessageService.getState(1L, 123L, 456L)).thenReturn(ReportedMessageState.NON_URGENT)
        whenever(reportRateLimitService.tryConsume(1L, 99L)).thenReturn(true)
        whenever(buttonEvent.jda).thenReturn(jda)
        whenever(jda.getChannelById(MessageChannel::class.java, 123L)).thenReturn(messageChannel)
        whenever(messageChannel.retrieveMessageById(456L)).thenReturn(retrieveMessageAction)
        doRetrieveMessageFailure()

        command.onButtonInteraction(buttonEvent)

        verify(buttonEvent).deferEdit()
        verify(interactionHook).editOriginal("I could not find that message anymore.")
        verify(hookEditAction).setComponents(emptyList<ActionRow>())
        verify(guildLogger, never()).logWithContent(
            any<EmbedBuilder>(),
            any<User>(),
            any<Guild>(),
            isNull<List<MessageEmbed>>(),
            any<GuildLogger.LogTypeAction>(),
            any<String>()
        )
        verify(reportedMessageService, never()).markUrgent(any(), any(), any())
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
    fun `member cannot report their own messages`() {
        whenever(event.name).thenReturn("Report Message")
        whenever(event.guild).thenReturn(guild)
        whenever(event.member).thenReturn(reporter)
        whenever(event.reply(any<String>())).thenReturn(replyAction)
        whenever(replyAction.setEphemeral(true)).thenReturn(replyAction)
        whenever(guild.idLong).thenReturn(1L)
        whenever(reportSettingsService.isReportingEnabled(1L)).thenReturn(true)
        whenever(event.target).thenReturn(targetMessage)
        whenever(targetMessage.author).thenReturn(targetUser)
        whenever(targetUser.idLong).thenReturn(99L)
        whenever(reporter.idLong).thenReturn(99L)

        command.onMessageContextInteraction(event)

        verify(event).reply("You cannot report your own messages.")
    }

    @Test
    fun `reporting disabled member cannot report messages`() {
        stubReportingDisabledContext("Report Message")

        command.onMessageContextInteraction(event)

        verify(event).reply("Message reporting is not enabled on this server.")
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
    fun `urgent confirmation rejects when reporting is disabled`() {
        stubUrgentConfirmationDisabled()

        command.onButtonInteraction(buttonEvent)

        verify(buttonEvent).editMessage("Message reporting is not enabled on this server.")
        verify(guildLogger, never()).logWithContent(
            any<EmbedBuilder>(),
            any<User>(),
            any<Guild>(),
            isNull<List<MessageEmbed>>(),
            any<GuildLogger.LogTypeAction>(),
            any<String>()
        )
        verify(reportedMessageService, never()).markUrgent(any(), any(), any())
    }

    @Test
    fun `cancel button clears confirmation message`() {
        whenever(buttonEvent.componentId).thenReturn("report:cancel:1:123:456:99")
        whenever(buttonEvent.user).thenReturn(reporterUser)
        whenever(reporterUser.idLong).thenReturn(99L)
        whenever(buttonEvent.editMessage("Urgent report cancelled.")).thenReturn(editAction)
        whenever(editAction.setComponents(emptyList<ActionRow>())).thenReturn(editAction)

        command.onButtonInteraction(buttonEvent)

        verify(buttonEvent).editMessage("Urgent report cancelled.")
        verify(editAction).setComponents(emptyList<ActionRow>())
    }

    @Test
    fun `report embed uses author name when reported member is null`() {
        stubReportWithNullMember("Report Message")

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
        assertEquals("Reporter", embed.fields[0].name)
        assertEquals("Duncan(reporter-user) (99)", embed.fields[0].value)
        assertEquals("Reported user", embed.fields[1].name)
        assertEquals("ReportedUser", embed.fields[1].value)
    }

    @Test
    fun `report is rejected when no moderator log channel is configured`() {
        stubReporterAndTargetIdsForModLogCheck("Report Message")
        whenever(guildLogger.hasModeratorLogChannel(guild)).thenReturn(false)

        command.onMessageContextInteraction(event)

        verify(event).reply(
            "Message reporting is not configured on this server. A moderator log channel is required."
        )
        verify(guildLogger, never()).logWithContent(
            any<EmbedBuilder>(),
            any<User>(),
            any<Guild>(),
            isNull<List<MessageEmbed>>(),
            any<GuildLogger.LogTypeAction>(),
            any<String>()
        )
        verify(reportedMessageService, never()).markReported(any(), any(), any(), any())
    }

    @Test
    fun `urgent confirmation succeeds when rate limit token is already active`() {
        stubUrgentConfirmation()
        whenever(reportedMessageService.getState(1L, 123L, 456L)).thenReturn(ReportedMessageState.NON_URGENT)
        whenever(reportRateLimitService.hasActiveToken(1L, 99L)).thenReturn(true)
        whenever(buttonEvent.jda).thenReturn(jda)
        whenever(jda.getChannelById(MessageChannel::class.java, 123L)).thenReturn(messageChannel)
        whenever(messageChannel.retrieveMessageById(456L)).thenReturn(retrieveMessageAction)
        doRetrieveMessageSuccess()
        whenever(reportSettingsService.getUrgentMention(guild)).thenReturn("@everyone")

        command.onButtonInteraction(buttonEvent)

        verify(guildLogger).logWithContent(
            any<EmbedBuilder>(),
            eq(targetUser),
            eq(guild),
            isNull<List<MessageEmbed>>(),
            eq(GuildLogger.LogTypeAction.MODERATOR),
            eq("@everyone")
        )
        verify(reportedMessageService).markUrgent(1L, 123L, 456L)
        verify(reportRateLimitService, never()).tryConsume(any(), any())
        verify(buttonEvent).deferEdit()
        verify(interactionHook).editOriginal("Your urgent report has been sent to the moderation team.")
        verify(hookEditAction).setComponents(emptyList<ActionRow>())
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
        whenever(guildLogger.hasModeratorLogChannel(guild)).thenReturn(true)
        whenever(reportedMessageService.getState(1L, 123L, 456L)).thenReturn(null)
    }

    private fun stubBlockedReporterContext(
        commandName: String,
        timedOut: Boolean = false,
        muted: Boolean = false,
        blocked: Boolean = false,
        enabled: Boolean = true
    ) {
        whenever(event.name).thenReturn(commandName)
        whenever(event.guild).thenReturn(guild)
        whenever(event.member).thenReturn(reporter)
        whenever(event.reply(any<String>())).thenReturn(replyAction)
        whenever(replyAction.setEphemeral(true)).thenReturn(replyAction)
        whenever(guild.idLong).thenReturn(1L)
        whenever(reportSettingsService.isReportingEnabled(1L)).thenReturn(enabled)
        whenever(reporter.isTimedOut).thenReturn(timedOut)
        if (!timedOut) {
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
        whenever(reportSettingsService.isReportingEnabled(1L)).thenReturn(true)
        whenever(reportSettingsService.isUserBlocked(1L, 99L)).thenReturn(false)
        whenever(guildLogger.hasModeratorLogChannel(guild)).thenReturn(true)
        stubTargetMessageIds()
        whenever(reportedMessageService.getState(1L, 123L, 456L)).thenReturn(null)
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
                whenever(reportSettingsService.isReportingEnabled(1L)).thenReturn(true)
                whenever(reportSettingsService.isUserBlocked(1L, 99L)).thenReturn(false)
                whenever(reportRateLimitService.tryConsume(1L, 99L)).thenReturn(true)
            }
        }
        whenever(reporter.user).thenReturn(reporterUser)
        whenever(reporter.nickname).thenReturn("Duncan")
        whenever(reporter.id).thenReturn("99")
        whenever(reporterUser.name).thenReturn("reporter-user")
    }

    private fun stubReporterAndTargetIds(commandName: String) {
        whenever(event.name).thenReturn(commandName)
        whenever(event.guild).thenReturn(guild)
        whenever(event.member).thenReturn(reporter)
        whenever(event.reply(any<String>())).thenReturn(replyAction)
        whenever(replyAction.setEphemeral(true)).thenReturn(replyAction)
        whenever(guild.idLong).thenReturn(1L)
        whenever(reporter.idLong).thenReturn(99L)
        whenever(reporter.isTimedOut).thenReturn(false)
        whenever(muteService.isUserMuted(1L, 99L)).thenReturn(false)
        whenever(reportSettingsService.isReportingEnabled(1L)).thenReturn(true)
        whenever(reportSettingsService.isUserBlocked(1L, 99L)).thenReturn(false)
        stubTargetMessageIds()
    }

    private fun stubTargetMessageIds() {
        whenever(event.target).thenReturn(targetMessage)
        whenever(targetMessage.author).thenReturn(targetUser)
        whenever(targetMessage.guildChannel).thenReturn(channel)
        whenever(channel.idLong).thenReturn(123L)
        whenever(targetMessage.idLong).thenReturn(456L)
        whenever(targetUser.idLong).thenReturn(2L)
    }

    private fun stubTargetMessage() {
        stubTargetMessageIds()
        stubTargetMessageDetails()
    }

    private fun stubTargetMessageDetails() {
        whenever(targetMessage.author).thenReturn(targetUser)
        whenever(targetMessage.member).thenReturn(targetMember)
        whenever(targetMember.nickname).thenReturn(null)
        whenever(targetMember.user).thenReturn(targetUser)
        whenever(targetUser.name).thenReturn("ReportedUser")
        whenever(channel.asMention).thenReturn("<#123>")
        whenever(targetMessage.jumpUrl).thenReturn("https://discord.com/channels/1/123/456")
        whenever(targetMessage.contentRaw).thenReturn("This should be reviewed")
        whenever(targetMessage.attachments).thenReturn(listOf(attachment))
        whenever(attachment.url).thenReturn("https://example.invalid/image.png")
    }

    private fun stubUrgentConfirmation(includeRetrievedMessageDetails: Boolean = true) {
        whenever(buttonEvent.componentId).thenReturn("report:urgent:1:123:456:99")
        whenever(buttonEvent.user).thenReturn(reporterUser)
        whenever(reporterUser.idLong).thenReturn(99L)
        whenever(buttonEvent.guild).thenReturn(guild)
        whenever(buttonEvent.member).thenReturn(reporter)
        whenever(buttonEvent.deferEdit()).thenReturn(deferredEditAction)
        whenever(interactionHook.editOriginal(any<String>())).thenReturn(hookEditAction)
        whenever(hookEditAction.setComponents(any<List<ActionRow>>())).thenReturn(hookEditAction)
        doAnswer {
            val success = it.arguments[0] as Consumer<InteractionHook>
            success.accept(interactionHook)
            null
        }.whenever(deferredEditAction).queue(any<Consumer<InteractionHook>>())
        whenever(guild.idLong).thenReturn(1L)
        whenever(reporter.idLong).thenReturn(99L)
        whenever(reporter.isTimedOut).thenReturn(false)
        whenever(muteService.isUserMuted(1L, 99L)).thenReturn(false)
        whenever(reportSettingsService.isReportingEnabled(1L)).thenReturn(true)
        whenever(reportSettingsService.isUserBlocked(1L, 99L)).thenReturn(false)
        whenever(guildLogger.hasModeratorLogChannel(guild)).thenReturn(true)
        if (includeRetrievedMessageDetails) {
            whenever(reporter.user).thenReturn(reporterUser)
            whenever(reporter.nickname).thenReturn("Duncan")
            whenever(reporterUser.name).thenReturn("reporter-user")
            whenever(targetMessage.guildChannel).thenReturn(channel)
            stubTargetMessageDetails()
        }
    }

    private fun stubReportWithNullMember(commandName: String) {
        stubReporterContext(commandName)
        whenever(event.target).thenReturn(targetMessage)
        whenever(targetMessage.author).thenReturn(targetUser)
        whenever(targetMessage.member).thenReturn(null)
        whenever(targetMessage.guildChannel).thenReturn(channel)
        whenever(channel.idLong).thenReturn(123L)
        whenever(targetMessage.idLong).thenReturn(456L)
        whenever(targetUser.idLong).thenReturn(2L)
        whenever(targetUser.name).thenReturn("ReportedUser")
        whenever(channel.asMention).thenReturn("<#123>")
        whenever(targetMessage.jumpUrl).thenReturn("https://discord.com/channels/1/123/456")
        whenever(targetMessage.contentRaw).thenReturn("This should be reviewed")
        whenever(targetMessage.attachments).thenReturn(listOf(attachment))
        whenever(attachment.url).thenReturn("https://example.invalid/image.png")
        whenever(guildLogger.hasModeratorLogChannel(guild)).thenReturn(true)
        whenever(reportedMessageService.getState(1L, 123L, 456L)).thenReturn(null)
    }

    private fun stubReporterAndTargetIdsForModLogCheck(commandName: String) {
        whenever(event.name).thenReturn(commandName)
        whenever(event.guild).thenReturn(guild)
        whenever(event.member).thenReturn(reporter)
        whenever(event.reply(any<String>())).thenReturn(replyAction)
        whenever(replyAction.setEphemeral(true)).thenReturn(replyAction)
        whenever(guild.idLong).thenReturn(1L)
        whenever(reporter.idLong).thenReturn(99L)
        whenever(reporter.isTimedOut).thenReturn(false)
        whenever(muteService.isUserMuted(1L, 99L)).thenReturn(false)
        whenever(reportSettingsService.isReportingEnabled(1L)).thenReturn(true)
        whenever(reportSettingsService.isUserBlocked(1L, 99L)).thenReturn(false)
        whenever(event.target).thenReturn(targetMessage)
        whenever(targetMessage.author).thenReturn(targetUser)
        whenever(targetMessage.guildChannel).thenReturn(channel)
        whenever(channel.idLong).thenReturn(123L)
        whenever(targetMessage.idLong).thenReturn(456L)
        whenever(targetUser.idLong).thenReturn(2L)
        whenever(reportedMessageService.getState(1L, 123L, 456L)).thenReturn(null)
    }

    private fun stubReportingDisabledContext(commandName: String) {
        whenever(event.name).thenReturn(commandName)
        whenever(event.guild).thenReturn(guild)
        whenever(event.member).thenReturn(reporter)
        whenever(event.reply(any<String>())).thenReturn(replyAction)
        whenever(replyAction.setEphemeral(true)).thenReturn(replyAction)
        whenever(guild.idLong).thenReturn(1L)
        whenever(reportSettingsService.isReportingEnabled(1L)).thenReturn(false)
    }

    private fun stubUrgentConfirmationDisabled() {
        whenever(buttonEvent.componentId).thenReturn("report:urgent:1:123:456:99")
        whenever(buttonEvent.user).thenReturn(reporterUser)
        whenever(reporterUser.idLong).thenReturn(99L)
        whenever(buttonEvent.guild).thenReturn(guild)
        whenever(buttonEvent.member).thenReturn(reporter)
        whenever(buttonEvent.editMessage(any<String>())).thenReturn(editAction)
        whenever(editAction.setComponents(any<List<ActionRow>>())).thenReturn(editAction)
        whenever(guild.idLong).thenReturn(1L)
        whenever(reportSettingsService.isReportingEnabled(1L)).thenReturn(false)
    }

    private fun doRetrieveMessageSuccess() {
        doAnswer {
            val success = it.arguments[0] as Consumer<Message>
            success.accept(targetMessage)
            null
        }.whenever(retrieveMessageAction).queue(any<Consumer<Message>>(), any<Consumer<Throwable>>())
    }

    private fun doRetrieveMessageFailure() {
        doAnswer {
            val failure = it.arguments[1] as Consumer<Throwable>
            failure.accept(RuntimeException("message not found"))
            null
        }.whenever(retrieveMessageAction).queue(any<Consumer<Message>>(), any<Consumer<Throwable>>())
    }
}
