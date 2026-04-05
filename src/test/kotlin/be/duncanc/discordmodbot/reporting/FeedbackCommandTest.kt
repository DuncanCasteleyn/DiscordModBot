package be.duncanc.discordmodbot.reporting

import be.duncanc.discordmodbot.reporting.persistence.ReportChannel
import be.duncanc.discordmodbot.reporting.persistence.ReportChannelRepository
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.Member
import net.dv8tion.jda.api.entities.MessageEmbed
import net.dv8tion.jda.api.entities.User
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.interactions.InteractionContextType
import net.dv8tion.jda.api.modals.Modal
import net.dv8tion.jda.api.requests.restaction.interactions.ReplyCallbackAction
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.awt.Color
import java.util.*

@ExtendWith(MockitoExtension::class)
class FeedbackCommandTest {
    @Mock
    private lateinit var reportChannelRepository: ReportChannelRepository

    @Mock
    private lateinit var slashEvent: SlashCommandInteractionEvent

    @Mock
    private lateinit var modalEvent: ModalInteractionEvent

    @Mock
    private lateinit var guild: Guild

    @Mock
    private lateinit var member: Member

    @Mock
    private lateinit var user: User

    @Mock
    private lateinit var textChannel: TextChannel

    @Mock
    private lateinit var replyAction: ReplyCallbackAction

    private lateinit var command: TestFeedbackCommand

    @BeforeEach
    fun setUp() {
        command = TestFeedbackCommand(reportChannelRepository)
    }

    @Test
    fun `non-matching slash name returns early`() {
        whenever(slashEvent.name).thenReturn("other")

        command.onSlashCommandInteraction(slashEvent)

        verify(slashEvent, never()).reply(any<String>())
    }

    @Test
    fun `disabled feedback returns error`() {
        stubReply(slash = true)
        whenever(slashEvent.name).thenReturn("feedback")
        whenever(slashEvent.guild).thenReturn(guild)
        whenever(slashEvent.member).thenReturn(member)
        whenever(guild.idLong).thenReturn(1L)
        whenever(reportChannelRepository.findById(1L)).thenReturn(Optional.empty())

        command.onSlashCommandInteraction(slashEvent)

        verify(slashEvent).reply("Feedback is not enabled on this server.")
    }

    @Test
    fun `enabled feedback opens modal`() {
        stubEnabledSlashCommand()

        command.onSlashCommandInteraction(slashEvent)

        assertNotNull(command.shownModal)
        assertEquals("Submit Feedback", command.shownModal!!.title)
    }

    @Test
    fun `modal submission forwards embed and confirms success`() {
        stubEnabledSlashCommand()
        command.onSlashCommandInteraction(slashEvent)
        stubModalContext()
        stubReply(modal = true)
        stubMemberIdentity()
        whenever(modalEvent.modalId).thenReturn(command.shownModal!!.id)
        command.feedbackMessage = "The server is great"

        command.onModalInteraction(modalEvent)

        assertEquals(11L, command.sentChannelId)
        assertEquals("The server is great", command.sentEmbed!!.description)
        assertEquals("Duncan(test-user)", command.sentEmbed!!.author!!.name)
        assertEquals("99", command.sentEmbed!!.footer!!.text)
        assertEquals(Color.GREEN, command.sentEmbed!!.color)
        verify(modalEvent).reply("Your feedback has been transferred to the moderators. Thank you for helping us.")
    }

    @Test
    fun `modal submission reports send failures`() {
        stubEnabledSlashCommand()
        command.onSlashCommandInteraction(slashEvent)
        stubModalContext()
        stubReply(modal = true)
        stubMemberIdentity()
        whenever(modalEvent.modalId).thenReturn(command.shownModal!!.id)
        command.feedbackMessage = "The server is great"
        command.failToSendFeedback = true

        command.onModalInteraction(modalEvent)

        verify(modalEvent).reply("I could not forward your feedback to the configured channel. Please contact server staff.")
    }

    @Test
    fun `mismatched modal user is rejected`() {
        stubEnabledSlashCommand()
        command.onSlashCommandInteraction(slashEvent)
        stubModalContext(includeRepository = false)
        stubReply(modal = true)
        whenever(modalEvent.modalId).thenReturn(command.shownModal!!.id.replace(":99", ":100"))

        command.onModalInteraction(modalEvent)

        verify(modalEvent).reply("This feedback form is out of date. Run `/feedback` again.")
    }

    @Test
    fun `command data is guild only`() {
        val commandData = command.getCommandsData().single()

        assertEquals("feedback", commandData.name)
        assertEquals(setOf(InteractionContextType.GUILD), commandData.contexts)
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

    private fun stubSlashCommandContext() {
        whenever(slashEvent.name).thenReturn("feedback")
        whenever(slashEvent.guild).thenReturn(guild)
        whenever(slashEvent.member).thenReturn(member)
        whenever(slashEvent.user).thenReturn(user)
    }

    private fun stubEnabledSlashCommand() {
        stubSlashCommandContext()
        whenever(guild.idLong).thenReturn(1L)
        whenever(user.idLong).thenReturn(99L)
        whenever(guild.getTextChannelById(11L)).thenReturn(textChannel)
        whenever(reportChannelRepository.findById(1L)).thenReturn(Optional.of(ReportChannel(1L, 11L)))
    }

    private fun stubModalContext(includeRepository: Boolean = true) {
        whenever(modalEvent.guild).thenReturn(guild)
        whenever(modalEvent.member).thenReturn(member)
        whenever(modalEvent.user).thenReturn(user)
        whenever(guild.idLong).thenReturn(1L)
        whenever(user.idLong).thenReturn(99L)
        if (includeRepository) {
            whenever(reportChannelRepository.findById(1L)).thenReturn(Optional.of(ReportChannel(1L, 11L)))
        }
    }

    private fun stubMemberIdentity() {
        whenever(user.id).thenReturn("99")
        whenever(user.name).thenReturn("test-user")
        whenever(user.effectiveAvatarUrl).thenReturn("https://example.com/avatar.png")
        whenever(member.user).thenReturn(user)
        whenever(member.nickname).thenReturn("Duncan")
    }

    private class TestFeedbackCommand(
        reportChannelRepository: ReportChannelRepository
    ) : FeedbackCommand(reportChannelRepository) {
        var shownModal: Modal? = null
        var feedbackMessage: String? = null
        var sentChannelId: Long? = null
        var sentEmbed: MessageEmbed? = null
        var failToSendFeedback = false

        override fun showModal(event: SlashCommandInteractionEvent, modal: Modal) {
            shownModal = modal
        }

        override fun getFeedbackMessage(event: ModalInteractionEvent): String? {
            return feedbackMessage
        }

        override fun sendFeedbackEmbed(
            guild: Guild,
            channelId: Long,
            embed: MessageEmbed,
            onSuccess: () -> Unit,
            onMissingChannel: () -> Unit,
            onFailure: () -> Unit
        ) {
            sentChannelId = channelId
            sentEmbed = embed
            if (failToSendFeedback) {
                onFailure()
            } else {
                onSuccess()
            }
        }
    }
}
