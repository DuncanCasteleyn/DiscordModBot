package be.duncanc.discordmodbot.voting

import be.duncanc.discordmodbot.voting.persistence.VotingEmotesRepository
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.Member
import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.entities.emoji.Emoji
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.interactions.InteractionContextType
import net.dv8tion.jda.api.interactions.InteractionHook
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData
import net.dv8tion.jda.api.requests.restaction.WebhookMessageEditAction
import net.dv8tion.jda.api.requests.restaction.WebhookMessageCreateAction
import net.dv8tion.jda.api.requests.restaction.interactions.ReplyCallbackAction
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.mock
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
    private lateinit var replyAction: ReplyCallbackAction

    @Mock
    private lateinit var interactionHook: InteractionHook

    @Mock
    private lateinit var editAction: WebhookMessageEditAction<Message>

    @Mock
    private lateinit var followupAction: WebhookMessageCreateAction<Message>

    private lateinit var command: TestVoteCommand

    @BeforeEach
    fun setUp() {
        command = TestVoteCommand(votingEmotesRepository)
    }

    @Test
    fun `non matching name is ignored`() {
        whenever(slashEvent.name).thenReturn("other")

        command.onSlashCommandInteraction(slashEvent)

        verify(slashEvent, never()).reply(any<String>())
    }

    @Test
    fun `yesno creates plain text vote with default reactions`() {
        stubSlashCommandContext("yesno")
        stubGuildId()
        stubVoteMessageContext()
        command.prompt = "Should we do this?"
        whenever(votingEmotesRepository.findById(1L)).thenReturn(Optional.empty())

        command.onSlashCommandInteraction(slashEvent)

        assertEquals("Vote started by <@99>\n\nShould we do this?", command.createdContent)
        assertEquals(listOf("✅", "❎"), command.capturedReactions)
    }

    @Test
    fun `yesno uses configured custom emoji when available`() {
        stubSlashCommandContext("yesno")
        stubGuildId()
        stubVoteMessageContext()
        command.prompt = "Use custom emoji?"
        command.yesNoReactionsOverride = listOf(Emoji.fromFormatted("<:yes:100>"), Emoji.fromFormatted("<:no:101>"))

        command.onSlashCommandInteraction(slashEvent)

        assertEquals(listOf("<:yes:100>", "<:no:101>"), command.capturedReactions)
    }

    @Test
    fun `numeric rejects invalid count`() {
        stubSlashCommandContext("numeric")
        stubReply()
        command.prompt = "Pick one"
        command.count = 1

        command.onSlashCommandInteraction(slashEvent)

        verify(slashEvent).reply("Please provide a number of voting options between 2 and 11.")
    }

    @Test
    fun `numeric vote preserves eleven option sequence`() {
        stubSlashCommandContext("numeric")
        stubVoteMessageContext()
        command.prompt = "Pick a number"
        command.count = 11

        command.onSlashCommandInteraction(slashEvent)

        assertTrue(command.createdContent!!.contains("Options: 1-11"))
        assertEquals(listOf("1⃣", "2⃣", "3⃣", "4⃣", "5⃣", "6⃣", "7⃣", "8⃣", "9⃣", "🔟", "0⃣"), command.capturedReactions)
    }

    @Test
    fun `reaction failure sends ephemeral followup`() {
        stubSlashCommandContext("yesno")
        stubGuildId()
        stubVoteMessageContext()
        stubFollowupReply()
        command.prompt = "Should we do this?"
        command.reactionFailure = IllegalStateException("boom")
        whenever(slashEvent.hook).thenReturn(interactionHook)
        whenever(votingEmotesRepository.findById(1L)).thenReturn(Optional.empty())

        command.onSlashCommandInteraction(slashEvent)

        verify(interactionHook).sendMessage("The vote message was posted, but I could not add all reactions.")
        verify(followupAction).setEphemeral(true)
    }

    @Test
    fun `vote message failure falls back to ephemeral reply`() {
        stubSlashCommandContext("yesno")
        stubGuildId()
        stubVoteMessageContext()
        stubReply()
        command.prompt = "Should we do this?"
        command.voteMessageFailure = IllegalStateException("boom")
        whenever(votingEmotesRepository.findById(1L)).thenReturn(Optional.empty())

        command.onSlashCommandInteraction(slashEvent)

        verify(slashEvent).reply("I could not post the vote message in this channel.")
        verify(replyAction).setEphemeral(true)
    }

    @Test
    @Suppress("UNCHECKED_CAST")
    fun `create vote message uses public deferred reply`() {
        val realCommand = VoteCommand(votingEmotesRepository)
        val message = mock<Message>()

        whenever(slashEvent.deferReply(false)).thenReturn(replyAction)
        whenever(interactionHook.editOriginal("Vote content")).thenReturn(editAction)
        doAnswer {
            val success = it.arguments[0] as Consumer<InteractionHook>
            success.accept(interactionHook)
            null
        }.whenever(replyAction).queue(any<Consumer<InteractionHook>>(), any())
        doAnswer {
            val success = it.arguments[0] as Consumer<Message>
            success.accept(message)
            null
        }.whenever(editAction).queue(any<Consumer<Message>>(), any())

        var capturedMessage: Message? = null

        realCommand.createVoteMessage(
            event = slashEvent,
            content = "Vote content",
            onSuccess = { capturedMessage = it },
            onFailure = { throw AssertionError("Unexpected failure", it) }
        )

        verify(slashEvent).deferReply(false)
        verify(interactionHook).editOriginal("Vote content")
        assertEquals(message, capturedMessage)
    }

    @Test
    fun `command data exposes expected vote subcommands`() {
        val commandData = command.getCommandsData().single()

        assertEquals("vote", commandData.name)
        assertEquals(setOf(InteractionContextType.GUILD), commandData.contexts)
        assertEquals(listOf("yesno", "numeric"), commandData.subcommands.map(SubcommandData::getName))
    }

    private fun stubSlashCommandContext(subcommandName: String) {
        whenever(slashEvent.name).thenReturn("vote")
        whenever(slashEvent.guild).thenReturn(guild)
        whenever(slashEvent.member).thenReturn(member)
        whenever(slashEvent.subcommandName).thenReturn(subcommandName)
    }

    private fun stubGuildId() {
        whenever(guild.idLong).thenReturn(1L)
    }

    private fun stubVoteMessageContext() {
        whenever(member.asMention).thenReturn("<@99>")
    }

    private fun stubReply() {
        whenever(slashEvent.reply(any<String>())).thenReturn(replyAction)
        whenever(replyAction.setEphemeral(true)).thenReturn(replyAction)
    }

    private fun stubFollowupReply() {
        whenever(interactionHook.sendMessage(any<String>())).thenReturn(followupAction)
        whenever(followupAction.setEphemeral(true)).thenReturn(followupAction)
    }

    private class TestVoteCommand(
        votingEmotesRepository: VotingEmotesRepository
    ) : VoteCommand(votingEmotesRepository) {
        var prompt: String? = null
        var count: Int? = null
        var createdContent: String? = null
        var capturedReactions: List<String> = emptyList()
        var yesNoReactionsOverride: List<Emoji>? = null
        var voteMessageFailure: Throwable? = null
        var reactionFailure: Throwable? = null

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
            voteMessageFailure?.let(onFailure) ?: onSuccess(mock())
        }

        override fun addReactions(
            message: Message,
            reactions: List<Emoji>,
            onSuccess: () -> Unit,
            onFailure: (Throwable) -> Unit
        ) {
            capturedReactions = reactions.map { it.formatted }
            reactionFailure?.let(onFailure) ?: onSuccess()
        }
    }
}
