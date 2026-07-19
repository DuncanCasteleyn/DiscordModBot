package be.duncanc.discordmodbot.moderation

import be.duncanc.discordmodbot.logging.GuildLogger
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.entities.*
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel
import net.dv8tion.jda.api.entities.channel.unions.MessageChannelUnion
import net.dv8tion.jda.api.entities.messages.MessageSearchResponse
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.interactions.InteractionHook
import net.dv8tion.jda.api.interactions.commands.OptionMapping
import net.dv8tion.jda.api.requests.restaction.MessageSearchAction
import net.dv8tion.jda.api.requests.restaction.WebhookMessageEditAction
import net.dv8tion.jda.api.requests.restaction.interactions.ReplyCallbackAction
import net.dv8tion.jda.api.requests.restaction.pagination.MessagePaginationAction
import net.dv8tion.jda.api.utils.Procedure
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Answers
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.quality.Strictness
import org.mockito.kotlin.*
import java.time.Duration
import java.time.OffsetDateTime
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import java.util.function.Consumer

@ExtendWith(MockitoExtension::class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PurgeChannelCommandTest {
    @Mock
    private lateinit var slashEvent: SlashCommandInteractionEvent

    @Mock
    private lateinit var member: Member

    @Mock
    private lateinit var selfMember: SelfMember

    @Mock
    private lateinit var guild: Guild

    @Mock
    private lateinit var channelUnion: MessageChannelUnion

    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private lateinit var textChannel: TextChannel

    @Mock
    private lateinit var replyAction: ReplyCallbackAction

    @Mock
    private lateinit var interactionHook: InteractionHook

    @Mock
    private lateinit var editAction: WebhookMessageEditAction<Message>

    @Mock
    private lateinit var messagePaginationAction: MessagePaginationAction

    @Mock
    private lateinit var messageSearchAction: MessageSearchAction

    @Mock
    private lateinit var targetOption: OptionMapping

    @Mock
    private lateinit var fromOption: OptionMapping

    @Mock
    private lateinit var toOption: OptionMapping

    @Mock
    private lateinit var message: Message

    @Mock
    private lateinit var oldMessage: Message

    @Mock
    private lateinit var otherMessage: Message

    @Mock
    private lateinit var veryOldMessage: Message

    @Mock
    private lateinit var pastBoundaryMessage: Message

    @Mock
    private lateinit var author: User

    @Mock
    private lateinit var moderatorUser: User

    @Mock
    private lateinit var mentions: Mentions

    @Mock
    private lateinit var jda: JDA

    @Mock
    private lateinit var guildLogger: GuildLogger

    @Mock
    private lateinit var amountOption: OptionMapping

    private lateinit var command: PurgeChannelCommand

    @BeforeEach
    fun setUp() {
        command = PurgeChannelCommand()
    }

    @Test
    fun `command name filter - non-matching name returns early`() {
        whenever(slashEvent.name).thenReturn("othercommand")

        command.onSlashCommandInteraction(slashEvent)

        verify(slashEvent, never()).reply(any<String>())
    }

    @Test
    fun `missing member returns guild error`() {
        whenever(slashEvent.name).thenReturn("purgechannel")
        whenever(slashEvent.member).thenReturn(null)
        stubImmediateReply()

        command.onSlashCommandInteraction(slashEvent)

        verify(slashEvent).reply("This command only works in a guild.")
    }

    @Test
    fun `missing manage messages permission returns error`() {
        stubGuildContext()
        stubImmediateReply()
        whenever(member.hasPermission(textChannel, Permission.MESSAGE_MANAGE)).thenReturn(false)

        command.onSlashCommandInteraction(slashEvent)

        verify(slashEvent).reply("You need manage messages permission in this channel to use this command.")
    }

    @Test
    fun `missing bot permissions returns error`() {
        stubGuildContext()
        stubImmediateReply()
        stubMemberPermission()
        whenever(guild.selfMember).thenReturn(selfMember)
        whenever(
            selfMember.hasPermission(
                textChannel,
                Permission.MESSAGE_MANAGE,
                Permission.MESSAGE_HISTORY
            )
        ).thenReturn(false)

        command.onSlashCommandInteraction(slashEvent)

        verify(slashEvent).reply("I need manage messages and read message history permissions in this channel to use this command.")
    }

    @Test
    fun `invalid amount returns error`() {
        stubGuildContext(subcommandName = "all")
        stubImmediateReply()
        stubMemberPermission()
        stubBotPermissions()
        whenever(slashEvent.getOption("amount")).thenReturn(amountOption)
        whenever(amountOption.asInt).thenReturn(101)

        command.onSlashCommandInteraction(slashEvent)

        verify(slashEvent).reply("Please provide an amount between 1 and 100.")
    }

    @Test
    fun `filtered mode rejects missing target user`() {
        stubGuildContext(subcommandName = "filtered")
        stubImmediateReply()
        stubMemberPermission()
        stubBotPermissions()
        whenever(slashEvent.getOption("amount")).thenReturn(amountOption)
        whenever(amountOption.asInt).thenReturn(10)
        whenever(slashEvent.getOption("target")).thenReturn(null)

        command.onSlashCommandInteraction(slashEvent)

        verify(slashEvent).reply("Please select a user to delete messages from.")
    }

    @Test
    fun `filtered mode only deletes messages from the last two weeks`() {
        stubGuildContext(subcommandName = "filtered")
        stubMemberPermission()
        stubBotPermissions()
        whenever(slashEvent.getOption("amount")).thenReturn(amountOption)
        whenever(amountOption.asInt).thenReturn(10)
        whenever(slashEvent.getOption("target")).thenReturn(targetOption)
        whenever(targetOption.asLong).thenReturn(99L)
        whenever(slashEvent.deferReply(true)).thenReturn(replyAction)
        doAnswer { (consumer: Consumer<InteractionHook>) ->
            consumer.accept(interactionHook)
            null
        }.whenever(replyAction).queue(any())
        whenever(interactionHook.editOriginal(any<String>())).thenReturn(editAction)
        whenever(textChannel.name).thenReturn("general")
        stubSearch(message, oldMessage)
        stubMessage(message, author, 300L, "message to remove")
        whenever(message.timeCreated).thenReturn(OffsetDateTime.now().minusDays(1))
        stubMessage(oldMessage, author, 200L, "old message")
        whenever(oldMessage.timeCreated).thenReturn(OffsetDateTime.now().minusWeeks(3))
        whenever(author.idLong).thenReturn(99L)
        whenever(member.nickname).thenReturn(null)
        whenever(member.user).thenReturn(moderatorUser)
        whenever(moderatorUser.name).thenReturn("ModeratorUser")
        whenever(slashEvent.user).thenReturn(moderatorUser)
        whenever(slashEvent.jda).thenReturn(jda)
        whenever(jda.registeredListeners).thenReturn(listOf(guildLogger))

        command.onSlashCommandInteraction(slashEvent)

        val transcriptCaptor = argumentCaptor<ByteArray>()
        verify(textChannel).purgeMessages(listOf(message))
        verify(interactionHook).editOriginal("Attempting to delete 1 message(s) from <@99>.")
        verify(guildLogger).log(
            any(),
            eq(moderatorUser),
            eq(guild),
            isNull(),
            eq(GuildLogger.LogTypeAction.MODERATOR),
            transcriptCaptor.capture()
        )
        assertTrue(String(transcriptCaptor.firstValue).contains("message to remove"))
    }

    @Test
    fun `all mode respects from and to message ids`() {
        stubGuildContext(subcommandName = "all")
        stubMemberPermission()
        stubBotPermissions()
        whenever(slashEvent.getOption("amount")).thenReturn(amountOption)
        whenever(amountOption.asInt).thenReturn(10)
        whenever(slashEvent.getOption("from")).thenReturn(fromOption)
        whenever(fromOption.asString).thenReturn("300")
        whenever(slashEvent.getOption("to")).thenReturn(toOption)
        whenever(toOption.asString).thenReturn("200")
        whenever(slashEvent.deferReply(true)).thenReturn(replyAction)
        doAnswer { (consumer: Consumer<InteractionHook>) ->
            consumer.accept(interactionHook)
            null
        }.whenever(replyAction).queue(any())
        whenever(interactionHook.editOriginal(any<String>())).thenReturn(editAction)
        whenever(textChannel.name).thenReturn("general")
        stubHistory(message, otherMessage, oldMessage, veryOldMessage)
        stubMessage(message, author, 300L, "message to remove")
        stubMessage(otherMessage, author, 250L, "message to keep")
        stubMessage(oldMessage, author, 200L, "message in range")
        stubMessage(veryOldMessage, author, 150L, "message too old for range")
        whenever(member.nickname).thenReturn(null)
        whenever(member.user).thenReturn(moderatorUser)
        whenever(moderatorUser.name).thenReturn("ModeratorUser")
        whenever(slashEvent.user).thenReturn(moderatorUser)
        whenever(slashEvent.jda).thenReturn(jda)
        whenever(jda.registeredListeners).thenReturn(listOf(guildLogger))

        command.onSlashCommandInteraction(slashEvent)

        verify(textChannel).purgeMessages(listOf(message, otherMessage, oldMessage))
        verify(interactionHook).editOriginal("Attempting to delete 3 message(s).")
    }

    @Test
    fun `filtered mode respects from and to message ids`() {
        stubGuildContext(subcommandName = "filtered")
        stubMemberPermission()
        stubBotPermissions()
        whenever(slashEvent.getOption("amount")).thenReturn(amountOption)
        whenever(amountOption.asInt).thenReturn(10)
        whenever(slashEvent.getOption("target")).thenReturn(targetOption)
        whenever(targetOption.asLong).thenReturn(99L)
        whenever(slashEvent.getOption("from")).thenReturn(fromOption)
        whenever(fromOption.asString).thenReturn("300")
        whenever(slashEvent.getOption("to")).thenReturn(toOption)
        whenever(toOption.asString).thenReturn("200")
        whenever(slashEvent.deferReply(true)).thenReturn(replyAction)
        doAnswer { (consumer: Consumer<InteractionHook>) ->
            consumer.accept(interactionHook)
            null
        }.whenever(replyAction).queue(any())
        whenever(interactionHook.editOriginal(any<String>())).thenReturn(editAction)
        whenever(textChannel.name).thenReturn("general")
        stubSearch(message, oldMessage)
        stubMessage(message, author, 300L, "message to remove")
        stubMessage(oldMessage, author, 200L, "message in range")
        whenever(author.idLong).thenReturn(99L)
        whenever(member.nickname).thenReturn(null)
        whenever(member.user).thenReturn(moderatorUser)
        whenever(moderatorUser.name).thenReturn("ModeratorUser")
        whenever(slashEvent.user).thenReturn(moderatorUser)
        whenever(slashEvent.jda).thenReturn(jda)
        whenever(jda.registeredListeners).thenReturn(listOf(guildLogger))

        command.onSlashCommandInteraction(slashEvent)

        verify(textChannel).purgeMessages(listOf(message, oldMessage))
        verify(interactionHook).editOriginal("Attempting to delete 2 message(s) from <@99>.")
        verify(messageSearchAction).maxId(301L)
        verify(messageSearchAction, atLeastOnce()).minId(199L)
    }

    @Test
    fun `filtered mode paginates search results`() {
        stubGuildContext(subcommandName = "filtered")
        stubMemberPermission()
        stubBotPermissions()
        whenever(slashEvent.getOption("amount")).thenReturn(amountOption)
        whenever(amountOption.asInt).thenReturn(30)
        whenever(slashEvent.getOption("target")).thenReturn(targetOption)
        whenever(targetOption.asLong).thenReturn(99L)
        whenever(slashEvent.deferReply(true)).thenReturn(replyAction)
        doAnswer { (consumer: Consumer<InteractionHook>) ->
            consumer.accept(interactionHook)
            null
        }.whenever(replyAction).queue(any())
        whenever(interactionHook.editOriginal(any<String>())).thenReturn(editAction)
        whenever(textChannel.name).thenReturn("general")

        val firstPage = (30 downTo 6).map { id -> createSearchMessage(id.toLong(), author) }
        val secondPage = (5 downTo 1).map { id -> createSearchMessage(id.toLong(), author) }
        stubSearchPages(firstPage, secondPage)

        whenever(author.idLong).thenReturn(99L)
        whenever(member.nickname).thenReturn(null)
        whenever(member.user).thenReturn(moderatorUser)
        whenever(moderatorUser.name).thenReturn("ModeratorUser")
        whenever(slashEvent.user).thenReturn(moderatorUser)
        whenever(slashEvent.jda).thenReturn(jda)
        whenever(jda.registeredListeners).thenReturn(listOf(guildLogger))

        command.onSlashCommandInteraction(slashEvent)

        val expectedMessages = firstPage + secondPage
        verify(textChannel).purgeMessages(expectedMessages)
        verify(interactionHook).editOriginal("Attempting to delete 30 message(s) from <@99>.")
        verify(messageSearchAction).maxId(6L)
        verify(messageSearchAction, never()).minId(any<Long>())
    }

    @Test
    fun `filtered mode skips messages from child threads`() {
        stubGuildContext(subcommandName = "filtered")
        stubMemberPermission()
        stubBotPermissions()
        whenever(slashEvent.getOption("amount")).thenReturn(amountOption)
        whenever(amountOption.asInt).thenReturn(10)
        whenever(slashEvent.getOption("target")).thenReturn(targetOption)
        whenever(targetOption.asLong).thenReturn(99L)
        whenever(slashEvent.deferReply(true)).thenReturn(replyAction)
        doAnswer { (consumer: Consumer<InteractionHook>) ->
            consumer.accept(interactionHook)
            null
        }.whenever(replyAction).queue(any())
        whenever(interactionHook.editOriginal(any<String>())).thenReturn(editAction)
        whenever(textChannel.name).thenReturn("general")

        val threadChannel = mock<MessageChannelUnion> { on { idLong } doReturn 999L }
        stubSearch(message, otherMessage)
        stubMessage(message, author, 300L, "channel message")
        stubMessage(otherMessage, author, 200L, "thread message")
        whenever(otherMessage.channel).thenReturn(threadChannel)
        whenever(author.idLong).thenReturn(99L)
        whenever(member.nickname).thenReturn(null)
        whenever(member.user).thenReturn(moderatorUser)
        whenever(moderatorUser.name).thenReturn("ModeratorUser")
        whenever(slashEvent.user).thenReturn(moderatorUser)
        whenever(slashEvent.jda).thenReturn(jda)
        whenever(jda.registeredListeners).thenReturn(listOf(guildLogger))

        command.onSlashCommandInteraction(slashEvent)

        verify(textChannel).purgeMessages(listOf(message))
        verify(interactionHook).editOriginal("Attempting to delete 1 message(s) from <@99>.")
    }

    @Test
    fun `filtered mode retries not ready search index and succeeds`() {
        stubGuildContext(subcommandName = "filtered")
        stubMemberPermission()
        stubBotPermissions()
        whenever(slashEvent.getOption("amount")).thenReturn(amountOption)
        whenever(amountOption.asInt).thenReturn(10)
        whenever(slashEvent.getOption("target")).thenReturn(targetOption)
        whenever(targetOption.asLong).thenReturn(99L)
        whenever(slashEvent.deferReply(true)).thenReturn(replyAction)
        doAnswer { (consumer: Consumer<InteractionHook>) ->
            consumer.accept(interactionHook)
            null
        }.whenever(replyAction).queue(any())
        whenever(interactionHook.editOriginal(any<String>())).thenReturn(editAction)
        whenever(textChannel.name).thenReturn("general")

        stubSearchActionChain()

        val notReady = mock<MessageSearchResponse.NotReady> {
            on { retryAfter } doReturn Duration.ZERO
        }
        val notReadyResponse = mock<MessageSearchResponse> {
            on { isNotReady } doReturn true
            on { asNotReady() } doReturn notReady
        }

        val queueResponses = listOf(notReadyResponse, emptySearchResponse()).iterator()
        doAnswer { invocation ->
            invocation.component1<Consumer<MessageSearchResponse>>().accept(queueResponses.next())
            null
        }.whenever(messageSearchAction).queue(any(), any())

        val readyResults = mock<MessageSearchResponse.Results> {
            on { messages } doReturn listOf(message)
        }
        val readyResponse = mock<MessageSearchResponse> {
            on { isNotReady } doReturn false
            on { asResults() } doReturn readyResults
        }
        doAnswer { invocation ->
            invocation.component3<Consumer<MessageSearchResponse>>().accept(readyResponse)
            null
        }.whenever(messageSearchAction).queueAfter(
            any<Long>(),
            any<TimeUnit>(),
            any<Consumer<MessageSearchResponse>>(),
            any<Consumer<Throwable>>()
        )

        stubMessage(message, author, 300L, "message to remove")
        whenever(author.idLong).thenReturn(99L)
        whenever(member.nickname).thenReturn(null)
        whenever(member.user).thenReturn(moderatorUser)
        whenever(moderatorUser.name).thenReturn("ModeratorUser")
        whenever(slashEvent.user).thenReturn(moderatorUser)
        whenever(slashEvent.jda).thenReturn(jda)
        whenever(jda.registeredListeners).thenReturn(listOf(guildLogger))

        command.onSlashCommandInteraction(slashEvent)

        verify(textChannel).purgeMessages(listOf(message))
        verify(interactionHook).editOriginal("Attempting to delete 1 message(s) from <@99>.")
        verify(messageSearchAction).queueAfter(
            any<Long>(),
            any<TimeUnit>(),
            any<Consumer<MessageSearchResponse>>(),
            any<Consumer<Throwable>>()
        )
    }

    @Test
    fun `filtered mode treats from above signed snowflake range as unbounded`() {
        stubGuildContext(subcommandName = "filtered")
        stubMemberPermission()
        stubBotPermissions()
        whenever(slashEvent.getOption("amount")).thenReturn(amountOption)
        whenever(amountOption.asInt).thenReturn(1)
        whenever(slashEvent.getOption("target")).thenReturn(targetOption)
        whenever(targetOption.asLong).thenReturn(99L)
        whenever(slashEvent.getOption("from")).thenReturn(fromOption)
        whenever(fromOption.asString).thenReturn("9223372036854775808")
        whenever(slashEvent.getOption("to")).thenReturn(toOption)
        whenever(toOption.asString).thenReturn("200")
        whenever(slashEvent.deferReply(true)).thenReturn(replyAction)
        doAnswer { (consumer: Consumer<InteractionHook>) ->
            consumer.accept(interactionHook)
            null
        }.whenever(replyAction).queue(any())
        whenever(interactionHook.editOriginal(any<String>())).thenReturn(editAction)
        whenever(textChannel.name).thenReturn("general")
        stubSearch(message)
        stubMessage(message, author, 300L, "message to remove")
        whenever(author.idLong).thenReturn(99L)
        whenever(member.nickname).thenReturn(null)
        whenever(member.user).thenReturn(moderatorUser)
        whenever(moderatorUser.name).thenReturn("ModeratorUser")
        whenever(slashEvent.user).thenReturn(moderatorUser)
        whenever(slashEvent.jda).thenReturn(jda)
        whenever(jda.registeredListeners).thenReturn(listOf(guildLogger))

        command.onSlashCommandInteraction(slashEvent)

        verify(messageSearchAction, never()).maxId(any<Long>())
        verify(messageSearchAction).minId(199L)
        verify(textChannel).purgeMessages(listOf(message))
        verify(interactionHook).editOriginal("Attempting to delete 1 message(s) from <@99>.")
    }

    @Test
    fun `filtered mode returns empty result for to above signed snowflake range`() {
        stubGuildContext(subcommandName = "filtered")
        stubMemberPermission()
        stubBotPermissions()
        whenever(slashEvent.getOption("amount")).thenReturn(amountOption)
        whenever(amountOption.asInt).thenReturn(1)
        whenever(slashEvent.getOption("target")).thenReturn(targetOption)
        whenever(targetOption.asLong).thenReturn(99L)
        whenever(slashEvent.getOption("to")).thenReturn(toOption)
        whenever(toOption.asString).thenReturn("18446744073709551615")
        whenever(slashEvent.deferReply(true)).thenReturn(replyAction)
        doAnswer { (consumer: Consumer<InteractionHook>) ->
            consumer.accept(interactionHook)
            null
        }.whenever(replyAction).queue(any())
        whenever(interactionHook.editOriginal(any<String>())).thenReturn(editAction)
        whenever(textChannel.name).thenReturn("general")
        whenever(member.nickname).thenReturn(null)
        whenever(member.user).thenReturn(moderatorUser)
        whenever(moderatorUser.name).thenReturn("ModeratorUser")
        whenever(slashEvent.user).thenReturn(moderatorUser)
        whenever(slashEvent.jda).thenReturn(jda)
        whenever(jda.registeredListeners).thenReturn(listOf(guildLogger))

        command.onSlashCommandInteraction(slashEvent)

        verify(guild, never()).searchMessages()
        verify(textChannel).purgeMessages(emptyList<Message>())
        verify(interactionHook).editOriginal("Attempting to delete 0 message(s) from <@99>.")
    }

    @Test
    fun `filtered mode reports not ready search index as error`() {
        stubGuildContext(subcommandName = "filtered")
        stubMemberPermission()
        stubBotPermissions()
        whenever(slashEvent.getOption("amount")).thenReturn(amountOption)
        whenever(amountOption.asInt).thenReturn(10)
        whenever(slashEvent.getOption("target")).thenReturn(targetOption)
        whenever(targetOption.asLong).thenReturn(99L)
        whenever(slashEvent.deferReply(true)).thenReturn(replyAction)
        doAnswer { (consumer: Consumer<InteractionHook>) ->
            consumer.accept(interactionHook)
            null
        }.whenever(replyAction).queue(any())
        whenever(interactionHook.editOriginal(any<String>())).thenReturn(editAction)
        whenever(textChannel.name).thenReturn("general")
        stubNotReadySearch()
        whenever(member.nickname).thenReturn(null)
        whenever(member.user).thenReturn(moderatorUser)
        whenever(moderatorUser.name).thenReturn("ModeratorUser")
        whenever(slashEvent.user).thenReturn(moderatorUser)
        whenever(slashEvent.jda).thenReturn(jda)
        whenever(jda.registeredListeners).thenReturn(listOf(guildLogger))

        command.onSlashCommandInteraction(slashEvent)

        verify(textChannel, never()).purgeMessages(any<List<Message>>())
        verify(interactionHook).editOriginal("Failed to purge messages: The search index is not ready yet, please try again later.")
        verify(messageSearchAction, times(3)).queueAfter(
            any<Long>(),
            any<TimeUnit>(),
            any<Consumer<MessageSearchResponse>>(),
            any<Consumer<Throwable>>()
        )
    }

    @Test
    fun `invalid from message id returns error and aborts purge`() {
        stubGuildContext(subcommandName = "all")
        stubImmediateReply()
        stubMemberPermission()
        stubBotPermissions()
        whenever(slashEvent.getOption("amount")).thenReturn(amountOption)
        whenever(amountOption.asInt).thenReturn(10)
        whenever(slashEvent.getOption("from")).thenReturn(fromOption)
        whenever(fromOption.asString).thenReturn("not-a-message-id")

        command.onSlashCommandInteraction(slashEvent)

        verify(slashEvent).reply("Please provide a valid message ID for from.")
        verify(slashEvent, never()).deferReply(any<Boolean>())
    }

    @Test
    fun `invalid to message id returns error and aborts purge`() {
        stubGuildContext(subcommandName = "all")
        stubImmediateReply()
        stubMemberPermission()
        stubBotPermissions()
        whenever(slashEvent.getOption("amount")).thenReturn(amountOption)
        whenever(amountOption.asInt).thenReturn(10)
        whenever(slashEvent.getOption("to")).thenReturn(toOption)
        whenever(toOption.asString).thenReturn("not-a-message-id")

        command.onSlashCommandInteraction(slashEvent)

        verify(slashEvent).reply("Please provide a valid message ID for to.")
        verify(slashEvent, never()).deferReply(any<Boolean>())
    }

    @Test
    fun `all mode handles unsigned snowflake overflow correctly`() {
        stubGuildContext(subcommandName = "all")
        stubMemberPermission()
        stubBotPermissions()
        whenever(slashEvent.getOption("amount")).thenReturn(amountOption)
        whenever(amountOption.asInt).thenReturn(10)
        whenever(slashEvent.getOption("from")).thenReturn(fromOption)
        whenever(fromOption.asString).thenReturn("18446744073709551615")
        whenever(slashEvent.getOption("to")).thenReturn(toOption)
        whenever(toOption.asString).thenReturn("100")
        whenever(slashEvent.deferReply(true)).thenReturn(replyAction)
        doAnswer { (consumer: Consumer<InteractionHook>) ->
            consumer.accept(interactionHook)
            null
        }.whenever(replyAction).queue(any())
        whenever(interactionHook.editOriginal(any<String>())).thenReturn(editAction)
        whenever(textChannel.name).thenReturn("general")
        val overflowMessage = message
        val middleMessage = oldMessage
        val toMessage = veryOldMessage
        val pastMessage = pastBoundaryMessage
        stubHistory(overflowMessage, middleMessage, toMessage, pastMessage)
        stubMessage(overflowMessage, author, -1L, "overflow message")
        stubMessage(middleMessage, author, 150L, "middle message")
        stubMessage(toMessage, author, 100L, "to message")
        stubMessage(pastMessage, author, 50L, "past message")
        whenever(member.nickname).thenReturn(null)
        whenever(member.user).thenReturn(moderatorUser)
        whenever(moderatorUser.name).thenReturn("ModeratorUser")
        whenever(slashEvent.user).thenReturn(moderatorUser)
        whenever(slashEvent.jda).thenReturn(jda)
        whenever(jda.registeredListeners).thenReturn(listOf(guildLogger))

        command.onSlashCommandInteraction(slashEvent)

        verify(textChannel).purgeMessages(listOf(overflowMessage, middleMessage, toMessage))
        verify(interactionHook).editOriginal("Attempting to delete 3 message(s).")
    }

    @Test
    fun `from must be newer than to`() {
        stubGuildContext(subcommandName = "all")
        stubImmediateReply()
        stubMemberPermission()
        stubBotPermissions()
        whenever(slashEvent.getOption("amount")).thenReturn(amountOption)
        whenever(amountOption.asInt).thenReturn(10)
        whenever(slashEvent.getOption("from")).thenReturn(fromOption)
        whenever(fromOption.asString).thenReturn("100")
        whenever(slashEvent.getOption("to")).thenReturn(toOption)
        whenever(toOption.asString).thenReturn("200")

        command.onSlashCommandInteraction(slashEvent)

        verify(slashEvent).reply("The from message must be newer than or the same as the to message.")
    }

    private companion object {
        const val CHANNEL_ID = 42L
    }

    private fun stubGuildContext(subcommandName: String) {
        whenever(slashEvent.name).thenReturn("purgechannel")
        whenever(slashEvent.member).thenReturn(member)
        whenever(slashEvent.guild).thenReturn(guild)
        whenever(slashEvent.channel).thenReturn(channelUnion)
        whenever(channelUnion.asTextChannel()).thenReturn(textChannel)
        whenever(slashEvent.subcommandName).thenReturn(subcommandName)
    }

    private fun stubGuildContext() {
        whenever(slashEvent.name).thenReturn("purgechannel")
        whenever(slashEvent.member).thenReturn(member)
        whenever(slashEvent.guild).thenReturn(guild)
        whenever(slashEvent.channel).thenReturn(channelUnion)
        whenever(channelUnion.asTextChannel()).thenReturn(textChannel)
    }

    private fun stubImmediateReply() {
        whenever(slashEvent.reply(any<String>())).thenReturn(replyAction)
        whenever(replyAction.setEphemeral(true)).thenReturn(replyAction)
    }

    private fun stubMemberPermission() {
        whenever(member.hasPermission(textChannel, Permission.MESSAGE_MANAGE)).thenReturn(true)
    }

    private fun stubBotPermissions() {
        whenever(guild.selfMember).thenReturn(selfMember)
        whenever(selfMember.hasPermission(textChannel, Permission.MESSAGE_MANAGE, Permission.MESSAGE_HISTORY))
            .thenReturn(true)
    }

    private fun stubHistory(vararg messages: Message) {
        whenever(textChannel.iterableHistory.cache(false)).thenReturn(messagePaginationAction)
        doAnswer { (procedure: Procedure<Message>) ->
            messages.forEach { message ->
                if (!procedure.execute(message)) {
                    return@doAnswer CompletableFuture.completedFuture(null)
                }
            }
            CompletableFuture.completedFuture(null)
        }.whenever(messagePaginationAction).forEachAsync(any())
    }

    private fun stubMessage(message: Message, author: User, idLong: Long, content: String) {
        whenever(message.idLong).thenReturn(idLong)
        whenever(message.author).thenReturn(author)
        whenever(message.contentDisplay).thenReturn(content)
        whenever(message.attachments).thenReturn(emptyList())
        whenever(message.mentions).thenReturn(mentions)
        whenever(message.timeCreated).thenReturn(OffsetDateTime.now().minusDays(1))
        whenever(message.channel).thenReturn(channelUnion)
        whenever(mentions.customEmojis).thenReturn(emptyList())
    }

    private fun createSearchMessage(idLong: Long, author: User): Message {
        val searchMessage = mock<Message>()
        whenever(searchMessage.idLong).thenReturn(idLong)
        whenever(searchMessage.author).thenReturn(author)
        whenever(searchMessage.contentDisplay).thenReturn("message $idLong")
        whenever(searchMessage.attachments).thenReturn(emptyList())
        whenever(searchMessage.mentions).thenReturn(mentions)
        whenever(searchMessage.timeCreated).thenReturn(OffsetDateTime.now().minusDays(1))
        whenever(searchMessage.channel).thenReturn(channelUnion)
        return searchMessage
    }

    private fun stubSearch(vararg messages: Message) {
        stubSearchPages(messages.toList())
    }

    private fun stubSearchActionChain() {
        whenever(textChannel.guild).thenReturn(guild)
        whenever(textChannel.idLong).thenReturn(CHANNEL_ID)
        whenever(channelUnion.idLong).thenReturn(CHANNEL_ID)
        whenever(guild.searchMessages()).thenReturn(messageSearchAction)
        whenever(messageSearchAction.channels(any<TextChannel>())).thenReturn(messageSearchAction)
        whenever(messageSearchAction.authors(any<Long>())).thenReturn(messageSearchAction)
        whenever(messageSearchAction.limit(any())).thenReturn(messageSearchAction)
        whenever(messageSearchAction.sortBy(any())).thenReturn(messageSearchAction)
        whenever(messageSearchAction.sortOrder(any())).thenReturn(messageSearchAction)
        whenever(messageSearchAction.maxId(any<Long>())).thenReturn(messageSearchAction)
        whenever(messageSearchAction.minId(any<Long>())).thenReturn(messageSearchAction)
    }

    private fun stubSearchPages(vararg pages: List<Message>) {
        stubSearchActionChain()

        val responses = pages.map { page ->
            val results = mock<MessageSearchResponse.Results> {
                on { messages } doReturn page
            }
            mock<MessageSearchResponse> {
                on { isNotReady } doReturn false
                on { asResults() } doReturn results
            }
        }

        val iterator = responses.iterator()
        doAnswer { invocation ->
            val consumer = invocation.component1<Consumer<MessageSearchResponse>>()
            consumer.accept(
                if (iterator.hasNext()) {
                    iterator.next()
                } else {
                    emptySearchResponse()
                }
            )
            null
        }.whenever(messageSearchAction).queue(any(), any())
    }

    private fun emptySearchResponse(): MessageSearchResponse {
        val results = mock<MessageSearchResponse.Results> {
            on { messages } doReturn emptyList()
        }
        return mock {
            on { isNotReady } doReturn false
            on { asResults() } doReturn results
        }
    }

    private fun stubNotReadySearch() {
        stubSearchActionChain()

        val notReady = mock<MessageSearchResponse.NotReady> {
            on { retryAfter } doReturn Duration.ZERO
        }
        val response = mock<MessageSearchResponse> {
            on { isNotReady } doReturn true
            on { asNotReady() } doReturn notReady
        }

        doAnswer { invocation ->
            invocation.component1<Consumer<MessageSearchResponse>>().accept(response)
            null
        }.whenever(messageSearchAction).queue(any(), any())

        doAnswer { invocation ->
            invocation.component3<Consumer<MessageSearchResponse>>().accept(response)
            null
        }.whenever(messageSearchAction).queueAfter(
            any<Long>(),
            any<TimeUnit>(),
            any<Consumer<MessageSearchResponse>>(),
            any<Consumer<Throwable>>()
        )
    }
}
