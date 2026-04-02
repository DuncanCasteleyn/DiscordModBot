package be.duncanc.discordmodbot.voting

import be.duncanc.discordmodbot.voting.persistence.VotingEmotesRepository
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.Member
import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.entities.channel.unions.MessageChannelUnion
import net.dv8tion.jda.api.entities.emoji.Emoji
import net.dv8tion.jda.api.entities.emoji.RichCustomEmoji
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.interactions.InteractionContextType
import net.dv8tion.jda.api.interactions.InteractionHook
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData
import net.dv8tion.jda.api.requests.restaction.WebhookMessageCreateAction
import net.dv8tion.jda.api.requests.restaction.interactions.ReplyCallbackAction
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.Mockito.lenient
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.*
import java.util.function.Consumer

@ExtendWith(MockitoExtension::class)
class VoteCommandTest {
    @Mock
    private lateinit var votingEmotesRepository: VotingEmotesRepository

    @Mock
    private lateinit var slashEvent: SlashCommandInteractionEvent

    @Mock
    private lateinit var guild: Guild

    @Mock
    private lateinit var member: Member

    @Mock
    private lateinit var channel: MessageChannelUnion

    @Mock
    private lateinit var replyAction: ReplyCallbackAction

    @Mock
    private lateinit var interactionHook: InteractionHook

    @Mock
    private lateinit var followupAction: WebhookMessageCreateAction<Message>

    @Mock
    private lateinit var jda: JDA

    @Mock
    private lateinit var yesEmoji: RichCustomEmoji

    @Mock
    private lateinit var noEmoji: RichCustomEmoji

    private lateinit var command: TestVoteCommand

    @BeforeEach
    fun setUp() {
        command = TestVoteCommand(votingEmotesRepository)

        lenient().whenever(slashEvent.name).thenReturn("vote")
        lenient().whenever(slashEvent.guild).thenReturn(guild)
        lenient().whenever(slashEvent.member).thenReturn(member)
        lenient().whenever(slashEvent.channel).thenReturn(channel)
        lenient().whenever(slashEvent.jda).thenReturn(jda)
        lenient().whenever(guild.idLong).thenReturn(1L)
        lenient().whenever(member.asMention).thenReturn("<@99>")
        lenient().whenever(channel.asMention).thenReturn("<#10>")
        lenient().whenever(slashEvent.reply(any<String>())).thenReturn(replyAction)
        lenient().whenever(replyAction.setEphemeral(true)).thenReturn(replyAction)
        lenient().whenever(slashEvent.deferReply(true)).thenReturn(replyAction)
        lenient().whenever(interactionHook.sendMessage(any<String>())).thenReturn(followupAction)
        lenient().whenever(followupAction.setEphemeral(true)).thenReturn(followupAction)
        lenient().doAnswer {
            val consumer = it.arguments[0] as Consumer<InteractionHook>
            consumer.accept(interactionHook)
            null
        }.whenever(replyAction).queue(any<Consumer<InteractionHook>>())
    }

    @Test
    fun `non matching name is ignored`() {
        whenever(slashEvent.name).thenReturn("other")

        command.onSlashCommandInteraction(slashEvent)

        verify(slashEvent, never()).reply(any<String>())
    }

    @Test
    fun `yesno creates plain text vote with default reactions`() {
        command.prompt = "Should we do this?"
        whenever(slashEvent.subcommandName).thenReturn("yesno")
        whenever(votingEmotesRepository.findById(1L)).thenReturn(Optional.empty())

        command.onSlashCommandInteraction(slashEvent)

        assertEquals("Vote started by <@99>\n\nShould we do this?", command.createdContent)
        assertEquals(listOf("✅", "❎"), command.capturedReactions)
        verify(interactionHook).sendMessage("Vote created in <#10>.")
    }

    @Test
    fun `yesno uses configured custom emoji when available`() {
        command.prompt = "Use custom emoji?"
        command.yesNoReactionsOverride = listOf(Emoji.fromFormatted("<:yes:100>"), Emoji.fromFormatted("<:no:101>"))
        whenever(slashEvent.subcommandName).thenReturn("yesno")

        command.onSlashCommandInteraction(slashEvent)

        assertEquals(listOf("<:yes:100>", "<:no:101>"), command.capturedReactions)
    }

    @Test
    fun `numeric rejects invalid count`() {
        command.prompt = "Pick one"
        command.count = 1
        whenever(slashEvent.subcommandName).thenReturn("numeric")

        command.onSlashCommandInteraction(slashEvent)

        verify(slashEvent).reply("Please provide a number of voting options between 2 and 11.")
    }

    @Test
    fun `numeric vote preserves eleven option sequence`() {
        command.prompt = "Pick a number"
        command.count = 11
        whenever(slashEvent.subcommandName).thenReturn("numeric")

        command.onSlashCommandInteraction(slashEvent)

        assertTrue(command.createdContent!!.contains("Options: 1-11"))
        assertEquals(listOf("1⃣", "2⃣", "3⃣", "4⃣", "5⃣", "6⃣", "7⃣", "8⃣", "9⃣", "🔟", "0⃣"), command.capturedReactions)
    }

    @Test
    fun `command data exposes expected vote subcommands`() {
        val commandData = command.getCommandsData().single()

        assertEquals("vote", commandData.name)
        assertEquals(setOf(InteractionContextType.GUILD), commandData.contexts)
        assertEquals(listOf("yesno", "numeric"), commandData.subcommands.map(SubcommandData::getName))
    }

    private class TestVoteCommand(
        votingEmotesRepository: VotingEmotesRepository
    ) : VoteCommand(votingEmotesRepository) {
        var prompt: String? = null
        var count: Int? = null
        var createdContent: String? = null
        var capturedReactions: List<String> = emptyList()
        var yesNoReactionsOverride: List<Emoji>? = null

        override fun getPrompt(event: SlashCommandInteractionEvent): String? {
            return prompt
        }

        override fun getCount(event: SlashCommandInteractionEvent): Int? {
            return count
        }

        override fun resolveYesNoReactions(guildId: Long, event: SlashCommandInteractionEvent): List<Emoji> {
            return yesNoReactionsOverride ?: super.resolveYesNoReactions(guildId, event)
        }

        override fun createVoteMessage(
            event: SlashCommandInteractionEvent,
            content: String,
            onSuccess: (Message) -> Unit,
            onFailure: (Throwable) -> Unit
        ) {
            createdContent = content
            onSuccess(org.mockito.kotlin.mock())
        }

        override fun addReactions(
            message: Message,
            reactions: List<Emoji>,
            onSuccess: () -> Unit,
            onFailure: (Throwable) -> Unit
        ) {
            capturedReactions = reactions.map { it.formatted }
            onSuccess()
        }
    }
}
