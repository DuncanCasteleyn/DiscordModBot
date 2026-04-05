package be.duncanc.discordmodbot.moderation

import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.entities.*
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel
import net.dv8tion.jda.api.entities.channel.unions.MessageChannelUnion
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.interactions.InteractionHook
import net.dv8tion.jda.api.interactions.commands.OptionMapping
import net.dv8tion.jda.api.requests.restaction.WebhookMessageEditAction
import net.dv8tion.jda.api.requests.restaction.interactions.ReplyCallbackAction
import net.dv8tion.jda.api.requests.restaction.pagination.MessagePaginationAction
import net.dv8tion.jda.api.utils.Procedure
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Answers
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.*
import java.util.concurrent.CompletableFuture
import java.util.function.Consumer

@ExtendWith(MockitoExtension::class)
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
    private lateinit var targetOption: OptionMapping

    @Mock
    private lateinit var message: Message

    @Mock
    private lateinit var author: User

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
    fun `filtered mode deletes targeted messages and replies with the correct count`() {
        stubGuildContext(subcommandName = "filtered")
        stubMemberPermission()
        stubBotPermissions()
        whenever(slashEvent.getOption("amount")).thenReturn(amountOption)
        whenever(amountOption.asInt).thenReturn(10)
        whenever(slashEvent.getOption("target")).thenReturn(targetOption)
        whenever(targetOption.asLong).thenReturn(99L)
        whenever(slashEvent.deferReply(true)).thenReturn(replyAction)
        doAnswer {
            val consumer = it.arguments[0] as Consumer<InteractionHook>
            consumer.accept(interactionHook)
            null
        }.whenever(replyAction).queue(any<Consumer<InteractionHook>>())
        whenever(interactionHook.editOriginal(any<String>())).thenReturn(editAction)
        whenever(textChannel.iterableHistory.cache(false)).thenReturn(messagePaginationAction)
        doAnswer {
            val procedure = it.arguments[0] as Procedure<Message>
            procedure.execute(message)
            CompletableFuture.completedFuture(null)
        }.whenever(messagePaginationAction).forEachAsync(any())
        whenever(message.author).thenReturn(author)
        whenever(author.idLong).thenReturn(99L)

        command.onSlashCommandInteraction(slashEvent)

        verify(textChannel).purgeMessages(listOf(message))
        verify(interactionHook).editOriginal("Attempting to delete 1 message(s) from <@99>.")
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
}
