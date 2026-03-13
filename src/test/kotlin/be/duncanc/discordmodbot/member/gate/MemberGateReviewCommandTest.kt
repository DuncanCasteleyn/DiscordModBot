package be.duncanc.discordmodbot.member.gate

import be.duncanc.discordmodbot.member.gate.persistence.MemberGateQuestion
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.components.actionrow.ActionRow
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.Member
import net.dv8tion.jda.api.entities.User
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent
import net.dv8tion.jda.api.requests.restaction.interactions.MessageEditCallbackAction
import net.dv8tion.jda.api.requests.restaction.interactions.ReplyCallbackAction
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.*

@ExtendWith(MockitoExtension::class)
class MemberGateReviewCommandTest {
    @Mock
    private lateinit var reviewManager: MemberGateReviewManager

    @Mock
    private lateinit var reviewSessionRegistry: MemberGateReviewSessionRegistry

    @Mock
    private lateinit var slashEvent: SlashCommandInteractionEvent

    @Mock
    private lateinit var buttonEvent: ButtonInteractionEvent

    @Mock
    private lateinit var guild: Guild

    @Mock
    private lateinit var member: Member

    @Mock
    private lateinit var user: User

    @Mock
    private lateinit var jda: JDA

    @Mock
    private lateinit var replyAction: ReplyCallbackAction

    @Mock
    private lateinit var editAction: MessageEditCallbackAction

    private lateinit var command: MemberGateReviewCommand

    @BeforeEach
    fun setUp() {
        command = MemberGateReviewCommand(reviewManager, reviewSessionRegistry)
    }

    private fun stubCommonIdentity() {
        whenever(guild.idLong).thenReturn(1L)
        whenever(user.idLong).thenReturn(99L)
        whenever(member.hasPermission(Permission.KICK_MEMBERS)).thenReturn(true)
    }

    private fun stubSlashReviewStart() {
        stubCommonIdentity()
        whenever(slashEvent.name).thenReturn("review")
        whenever(slashEvent.guild).thenReturn(guild)
        whenever(slashEvent.member).thenReturn(member)
        whenever(slashEvent.user).thenReturn(user)
        whenever(replyAction.setEphemeral(true)).thenReturn(replyAction)
        whenever(replyAction.addComponents(any<ActionRow>())).thenReturn(replyAction)
        whenever(slashEvent.reply(any<String>())).thenReturn(replyAction)
    }

    private fun stubApproveButtonInteraction() {
        stubCommonIdentity()
        whenever(buttonEvent.componentId).thenReturn("member-gate-review:approve")
        whenever(buttonEvent.guild).thenReturn(guild)
        whenever(buttonEvent.member).thenReturn(member)
        whenever(buttonEvent.user).thenReturn(user)
        whenever(buttonEvent.jda).thenReturn(jda)
    }

    private fun stubApproveButtonInteractionWithoutJda() {
        stubCommonIdentity()
        whenever(buttonEvent.componentId).thenReturn("member-gate-review:approve")
        whenever(buttonEvent.guild).thenReturn(guild)
        whenever(buttonEvent.member).thenReturn(member)
        whenever(buttonEvent.user).thenReturn(user)
    }

    private fun stubLegacySkipButtonInteraction() {
        stubCommonIdentity()
        whenever(buttonEvent.componentId).thenReturn("member-gate-review:skip")
        whenever(buttonEvent.guild).thenReturn(guild)
        whenever(buttonEvent.member).thenReturn(member)
        whenever(buttonEvent.user).thenReturn(user)
    }

    private fun stubButtonReply() {
        whenever(replyAction.setEphemeral(true)).thenReturn(replyAction)
        whenever(buttonEvent.reply(any<String>())).thenReturn(replyAction)
    }

    private fun stubButtonEditWithActionRow() {
        whenever(editAction.setComponents(any<ActionRow>())).thenReturn(editAction)
        whenever(buttonEvent.editMessage(any<String>())).thenReturn(editAction)
    }

    private fun stubButtonEditWithList() {
        whenever(editAction.setComponents(any<List<ActionRow>>())).thenReturn(editAction)
        whenever(buttonEvent.editMessage(any<String>())).thenReturn(editAction)
    }

    @Test
    fun `approve keeps review open when another applicant is still pending`() {
        stubSlashReviewStart()
        stubApproveButtonInteraction()
        stubButtonEditWithActionRow()

        val session = MemberGateReviewSession(listOf(10L, 20L))
        whenever(reviewManager.createSession(1L)).thenReturn(session)
        whenever(reviewSessionRegistry.get(1L, 99L)).thenReturn(session)
        whenever(reviewManager.getPendingQuestion(1L, 10L)).thenReturn(MemberGateQuestion(10L, "Q1", "A1", 1L, 10L))
        whenever(reviewManager.getPendingQuestion(1L, 20L)).thenReturn(MemberGateQuestion(20L, "Q2", "A2", 1L, 20L))
        whenever(reviewManager.approve(eq(guild), eq(jda), eq(10L))).thenReturn("Approved <@10>.")

        command.onSlashCommandInteraction(slashEvent)
        command.onButtonInteraction(buttonEvent)

        val messageCaptor = argumentCaptor<String>()
        verify(buttonEvent).editMessage(messageCaptor.capture())
        val editedMessage = messageCaptor.firstValue
        assertTrue(editedMessage.contains("Applicant: <@20> (`20`)"))
        assertTrue(editedMessage.contains("Continuing with the next pending applicant."))
        verify(reviewSessionRegistry, times(2)).remember(eq(1L), eq(99L), any())
        verify(buttonEvent, never()).reply("This review session expired. Run `/review` again.")
    }

    @Test
    fun `approve completes review when the final applicant is handled`() {
        stubSlashReviewStart()
        stubApproveButtonInteraction()
        stubButtonEditWithList()

        val session = MemberGateReviewSession(listOf(10L))
        whenever(reviewManager.createSession(1L)).thenReturn(session)
        whenever(reviewSessionRegistry.get(1L, 99L)).thenReturn(session)
        whenever(reviewManager.getPendingQuestion(1L, 10L)).thenReturn(MemberGateQuestion(10L, "Q1", "A1", 1L, 10L))
        whenever(reviewManager.approve(eq(guild), eq(jda), eq(10L))).thenReturn("Approved <@10>.")

        command.onSlashCommandInteraction(slashEvent)
        command.onButtonInteraction(buttonEvent)

        val messageCaptor = argumentCaptor<String>()
        verify(buttonEvent).editMessage(messageCaptor.capture())
        val editedMessages = messageCaptor.allValues
        val completionMessage = editedMessages.last()
        assertTrue(completionMessage.contains("Approved <@10>."))
        assertTrue(completionMessage.contains("There are no more pending applicants in the queue."))
        verify(reviewSessionRegistry).forget(1L, 99L)
    }

    @Test
    fun `expired session asks moderator to rerun review`() {
        stubApproveButtonInteractionWithoutJda()
        stubButtonReply()

        whenever(reviewSessionRegistry.get(1L, 99L)).thenReturn(null)

        command.onButtonInteraction(buttonEvent)

        verify(buttonEvent).reply("This review session expired. Run `/review` again.")
    }

    @Test
    fun `legacy skip button asks moderator to rerun review`() {
        stubLegacySkipButtonInteraction()
        stubButtonReply()

        whenever(reviewSessionRegistry.get(1L, 99L)).thenReturn(MemberGateReviewSession(listOf(10L)))

        command.onButtonInteraction(buttonEvent)

        verify(buttonEvent).reply("This review action is no longer available. Run `/review` again.")
    }
}
