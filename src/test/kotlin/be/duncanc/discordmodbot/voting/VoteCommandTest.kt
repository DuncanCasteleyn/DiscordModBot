package be.duncanc.discordmodbot.voting

import be.duncanc.discordmodbot.voting.persistence.VotingEmotesRepository
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.Member
import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.entities.emoji.Emoji
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.interactions.InteractionContextType
import net.dv8tion.jda.api.interactions.InteractionHook
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData
import net.dv8tion.jda.api.interactions.modals.ModalMapping
import net.dv8tion.jda.api.requests.restaction.WebhookMessageCreateAction
import net.dv8tion.jda.api.requests.restaction.WebhookMessageEditAction
import net.dv8tion.jda.api.requests.restaction.interactions.ModalCallbackAction
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
    private lateinit var modalEvent: ModalInteractionEvent

    @Mock
    private lateinit var guild: Guild

    @Mock
    private lateinit var member: Member

    @Mock
    private lateinit var replyAction: ReplyCallbackAction

    @Mock
    private lateinit var modalAction: ModalCallbackAction

    @Mock
    private lateinit var interactionHook: InteractionHook

    @Mock
    private lateinit var editAction: WebhookMessageEditAction<Message>

    @Mock
    private lateinit var followupAction: WebhookMessageCreateAction<Message>

    @Mock
    private lateinit var promptValue: ModalMapping

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
        verify(slashEvent, never()).replyModal(any())
    }

    @Test
    fun `yesno opens vote modal`() {
        stubSlashCommandContext("yesno")
        whenever(member.asMention).thenReturn("<@99>")
        whenever(slashEvent.replyModal(any())).thenReturn(modalAction)

        command.onSlashCommandInteraction(slashEvent)

        verify(slashEvent).replyModal(org.mockito.kotlin.argThat {
            id == "vote:yesno" &&
                    title == "Create Vote" &&
                    components.single().asLabel().label == "Vote prompt"
        })
    }

    @Test
    fun `numeric opens vote modal with count in id`() {
        stubSlashCommandContext("numeric")
        whenever(member.asMention).thenReturn("<@99>")
        command.count = 5
        whenever(slashEvent.replyModal(any())).thenReturn(modalAction)

        command.onSlashCommandInteraction(slashEvent)

        verify(slashEvent).replyModal(org.mockito.kotlin.argThat {
            id == "vote:numeric:5" && title == "Create Numeric Vote"
        })
    }

    @Test
    fun `numeric rejects invalid count`() {
        stubSlashCommandContext("numeric")
        stubReply(slash = true)
        command.count = 1

        command.onSlashCommandInteraction(slashEvent)

        verify(slashEvent).reply("Please provide a number of voting options between 2 and 11.")
    }

    @Test
    fun `modal rejects blank prompt`() {
        stubModalContext("vote:yesno")
        stubReply(modal = true)
        whenever(modalEvent.getValue("prompt")).thenReturn(promptValue)
        whenever(promptValue.asString).thenReturn("   ")

        command.onModalInteraction(modalEvent)

        verify(modalEvent).reply("Please provide a vote prompt.")
    }

    @Test
    fun `yesno modal creates plain text vote with default reactions`() {
        stubModalContext("vote:yesno")
        stubGuildId()
        stubVoteMessageContext()
        command.modalPrompt = "Should we do this?"
        whenever(votingEmotesRepository.findById(1L)).thenReturn(Optional.empty())

        command.onModalInteraction(modalEvent)

        assertEquals("Vote started by <@99>\n\nShould we do this?", command.createdContent)
        assertEquals(listOf("✅", "❎"), command.capturedReactions)
    }

    @Test
    fun `yesno modal uses configured custom emoji when available`() {
        stubModalContext("vote:yesno")
        stubGuildId()
        stubVoteMessageContext()
        command.modalPrompt = "Use custom emoji?"
        command.yesNoReactionsOverride = listOf(Emoji.fromFormatted("<:yes:100>"), Emoji.fromFormatted("<:no:101>"))

        command.onModalInteraction(modalEvent)

        assertEquals(listOf("<:yes:100>", "<:no:101>"), command.capturedReactions)
    }

    @Test
    fun `numeric modal preserves eleven option sequence`() {
        stubModalContext("vote:numeric:11")
        stubVoteMessageContext()
        command.modalPrompt = "Pick a number"

        command.onModalInteraction(modalEvent)

        assertTrue(command.createdContent!!.contains("Options: 1-11"))
        assertEquals(listOf("1⃣", "2⃣", "3⃣", "4⃣", "5⃣", "6⃣", "7⃣", "8⃣", "9⃣", "🔟", "0⃣"), command.capturedReactions)
    }

    @Test
    fun `reaction failure sends ephemeral followup`() {
        stubModalContext("vote:yesno")
        stubGuildId()
        stubVoteMessageContext()
        stubFollowupReply()
        command.modalPrompt = "Should we do this?"
        command.reactionFailure = IllegalStateException("boom")
        whenever(modalEvent.hook).thenReturn(interactionHook)
        whenever(votingEmotesRepository.findById(1L)).thenReturn(Optional.empty())

        command.onModalInteraction(modalEvent)

        verify(interactionHook).sendMessage("The vote message was posted, but I could not add all reactions.")
        verify(followupAction).setEphemeral(true)
    }

    @Test
    fun `vote message failure falls back to ephemeral reply`() {
        stubModalContext("vote:yesno")
        stubGuildId()
        stubVoteMessageContext()
        stubFollowupReply()
        command.modalPrompt = "Should we do this?"
        command.voteMessageFailure = IllegalStateException("boom")
        whenever(modalEvent.hook).thenReturn(interactionHook)
        whenever(votingEmotesRepository.findById(1L)).thenReturn(Optional.empty())

        command.onModalInteraction(modalEvent)

        verify(interactionHook).sendMessage("I could not post the vote message in this channel.")
        verify(followupAction).setEphemeral(true)
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
            deferReply = slashEvent::deferReply,
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
        assertTrue(commandData.subcommands.flatMap { it.options }.none { it.name == "prompt" })
        assertEquals(listOf("count"), commandData.subcommands.single { it.name == "numeric" }.options.map { it.name })
    }

    private fun stubSlashCommandContext(subcommandName: String) {
        whenever(slashEvent.name).thenReturn("vote")
        whenever(slashEvent.guild).thenReturn(guild)
        whenever(slashEvent.member).thenReturn(member)
        whenever(slashEvent.subcommandName).thenReturn(subcommandName)
    }

    private fun stubModalContext(modalId: String) {
        whenever(modalEvent.modalId).thenReturn(modalId)
        whenever(modalEvent.guild).thenReturn(guild)
        whenever(modalEvent.member).thenReturn(member)
        whenever(modalEvent.getValue("prompt")).thenReturn(promptValue)
        whenever(promptValue.asString).thenAnswer { command.modalPrompt }
    }

    private fun stubGuildId() {
        whenever(guild.idLong).thenReturn(1L)
    }

    private fun stubVoteMessageContext() {
        whenever(member.asMention).thenReturn("<@99>")
    }

    private fun stubReply(slash: Boolean = false, modal: Boolean = false) {
        if (slash) {
            whenever(slashEvent.reply(any<String>())).thenReturn(replyAction)
        }
        if (modal) {
            whenever(modalEvent.reply(any<String>())).thenReturn(replyAction)
        }
        whenever(replyAction.setEphemeral(true)).thenReturn(replyAction)
    }

    private fun stubFollowupReply() {
        whenever(interactionHook.sendMessage(any<String>())).thenReturn(followupAction)
        whenever(followupAction.setEphemeral(true)).thenReturn(followupAction)
    }

    private class TestVoteCommand(
        votingEmotesRepository: VotingEmotesRepository
    ) : VoteCommand(votingEmotesRepository) {
        var count: Int? = null
        var modalPrompt: String? = null
        var createdContent: String? = null
        var capturedReactions: List<String> = emptyList()
        var yesNoReactionsOverride: List<Emoji>? = null
        var voteMessageFailure: Throwable? = null
        var reactionFailure: Throwable? = null

        override fun getCount(event: SlashCommandInteractionEvent): Int? {
            return count
        }

        override fun resolveYesNoReactions(guildId: Long, event: SlashCommandInteractionEvent): List<Emoji> {
            return yesNoReactionsOverride ?: super.resolveYesNoReactions(guildId, event)
        }

        override fun resolveYesNoReactions(guildId: Long, event: ModalInteractionEvent): List<Emoji> {
            return yesNoReactionsOverride ?: super.resolveYesNoReactions(guildId, event)
        }

        override fun createVoteMessage(
            deferReply: (Boolean) -> ReplyCallbackAction,
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
