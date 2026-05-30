package be.duncanc.discordmodbot.member.gate

import be.duncanc.discordmodbot.logging.GuildLogger
import be.duncanc.discordmodbot.member.gate.persistence.MemberGateQuestion
import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.components.actionrow.ActionRow
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.Member
import net.dv8tion.jda.api.entities.MessageEmbed
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
class ReviewCommandTest {
    @Mock
    private lateinit var reviewManager: ReviewManager

    @Mock
    private lateinit var reviewSessionRegistry: ReviewSessionRegistry

    @Mock
    private lateinit var guildLogger: GuildLogger

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

    private lateinit var command: ReviewCommand

    @BeforeEach
    fun setUp() {
        command = ReviewCommand(reviewManager, reviewSessionRegistry, guildLogger)
    }

    private fun pendingQuestion(guildId: Long, userId: Long, question: String, answer: String, queuedAt: Long): MemberGateQuestion {
        return MemberGateQuestion(
            id = MemberGateQuestion.createId(guildId, userId),
            userId = userId,
            question = question,
            answer = answer,
            guildId = guildId,
            queuedAt = queuedAt
        )
    }

    private fun stubApplicantPresent(userId: Long, mention: String) {
        val applicantMember = mock<Member>()
        val applicantUser = mock<User>()
        whenever(guild.getMemberById(userId)).thenReturn(applicantMember)
        whenever(applicantMember.user).thenReturn(applicantUser)
        whenever(applicantUser.asMention).thenReturn(mention)
    }

    private fun stubCommonIdentity() {
        whenever(guild.idLong).thenReturn(1L)
        whenever(user.idLong).thenReturn(99L)
        whenever(member.hasPermission(Permission.MANAGE_ROLES)).thenReturn(true)
    }

    private fun stubModeratorName() {
        whenever(user.name).thenReturn("Moderator")
        whenever(member.user).thenReturn(user)
        whenever(member.nickname).thenReturn(null)
    }

    private fun stubSlashReviewStart() {
        stubCommonIdentity()
        stubModeratorName()
        whenever(slashEvent.name).thenReturn("review")
        whenever(slashEvent.guild).thenReturn(guild)
        whenever(slashEvent.member).thenReturn(member)
        whenever(slashEvent.user).thenReturn(user)
        whenever(slashEvent.jda).thenReturn(jda)
        whenever(reviewSessionRegistry.forgetOtherSessions(1L, 99L)).thenReturn(emptyList())
        whenever(replyAction.setEphemeral(true)).thenReturn(replyAction)
        whenever(replyAction.addComponents(any<ActionRow>())).thenReturn(replyAction)
        whenever(slashEvent.reply(any<String>())).thenReturn(replyAction)
    }

    private fun stubApproveButtonInteraction() {
        stubCommonIdentity()
        whenever(buttonEvent.componentId).thenReturn("member-gate-review:approve:10:10")
        whenever(buttonEvent.guild).thenReturn(guild)
        whenever(buttonEvent.member).thenReturn(member)
        whenever(buttonEvent.user).thenReturn(user)
        whenever(buttonEvent.jda).thenReturn(jda)
    }

    private fun stubApproveButtonInteractionWithoutJda() {
        stubCommonIdentity()
        whenever(buttonEvent.componentId).thenReturn("member-gate-review:approve:10:10")
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

        val session = ReviewSession(listOf(10L, 20L))
        whenever(reviewManager.createSession(1L)).thenReturn(session)
        whenever(reviewSessionRegistry.get(1L, 99L)).thenReturn(session)
        whenever(reviewManager.getPendingQuestion(1L, 10L)).thenReturn(
            pendingQuestion(guildId = 1L, userId = 10L, question = "Q1", answer = "A1", queuedAt = 10L)
        )
        whenever(reviewManager.getPendingQuestion(1L, 20L)).thenReturn(
            pendingQuestion(guildId = 1L, userId = 20L, question = "Q2", answer = "A2", queuedAt = 20L)
        )
        stubApplicantPresent(10L, "<@10>")
        stubApplicantPresent(20L, "<@20>")
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

        val session = ReviewSession(listOf(10L))
        whenever(reviewManager.createSession(1L)).thenReturn(session)
        whenever(reviewSessionRegistry.get(1L, 99L)).thenReturn(session)
        whenever(reviewManager.getPendingQuestion(1L, 10L)).thenReturn(
            pendingQuestion(guildId = 1L, userId = 10L, question = "Q1", answer = "A1", queuedAt = 10L)
        )
        stubApplicantPresent(10L, "<@10>")
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
        verifyReviewLog("Member gate review completed", "Approved", "1")
    }

    @Test
    fun `starting review logs moderator and pending applicant count`() {
        stubSlashReviewStart()

        val session = ReviewSession(listOf(10L, 20L))
        whenever(reviewManager.createSession(1L)).thenReturn(session)
        whenever(reviewManager.getPendingQuestion(1L, 10L)).thenReturn(
            pendingQuestion(guildId = 1L, userId = 10L, question = "Q1", answer = "A1", queuedAt = 10L)
        )
        stubApplicantPresent(10L, "<@10>")

        command.onSlashCommandInteraction(slashEvent)

        verifyReviewLog("Member gate review started", "Pending applicants", "2")
    }

    @Test
    fun `starting review logs and removes another moderator's unfinished session`() {
        stubSlashReviewStart()

        val interruptedSession = ReviewSession(
            pendingUserIds = listOf(50L),
            approvedCount = 2,
            rejectedCount = 1,
            manualActionCount = 3
        )
        whenever(reviewSessionRegistry.forgetOtherSessions(1L, 99L)).thenReturn(
            listOf(ReviewSessionRegistry.StoredReviewSession(42L, interruptedSession))
        )
        val session = ReviewSession(listOf(10L))
        whenever(reviewManager.createSession(1L)).thenReturn(session)
        whenever(reviewManager.getPendingQuestion(1L, 10L)).thenReturn(
            pendingQuestion(guildId = 1L, userId = 10L, question = "Q1", answer = "A1", queuedAt = 10L)
        )
        whenever(guild.getMemberById(42L)).thenReturn(null)
        stubApplicantPresent(10L, "<@10>")

        command.onSlashCommandInteraction(slashEvent)

        verifyReviewLog("Member gate review interrupted", "Approved", "2")
        verifyReviewLog("Member gate review interrupted", "Rejected", "1")
        verifyReviewLog("Member gate review interrupted", "Manual action", "3")
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

        whenever(reviewSessionRegistry.get(1L, 99L)).thenReturn(ReviewSession(listOf(10L)))

        command.onButtonInteraction(buttonEvent)

        verify(buttonEvent).reply("This review action is no longer available. Run `/review` again.")
    }

    @Test
    fun `stale button asks moderator to rerun review instead of reviewing a different applicant`() {
        stubButtonReply()
        stubCommonIdentity()
        whenever(buttonEvent.componentId).thenReturn("member-gate-review:approve:10:10")
        whenever(buttonEvent.guild).thenReturn(guild)
        whenever(buttonEvent.member).thenReturn(member)
        whenever(buttonEvent.user).thenReturn(user)

        whenever(reviewSessionRegistry.get(1L, 99L)).thenReturn(ReviewSession(listOf(20L, 30L)))

        command.onButtonInteraction(buttonEvent)

        verify(buttonEvent).reply("This review message is out of date. Run `/review` again.")
        verify(reviewManager, never()).approve(any(), any(), any())
    }

    @Test
    fun `stale button does not review a newer submission from the same applicant`() {
        stubButtonReply()
        stubApproveButtonInteractionWithoutJda()

        whenever(reviewSessionRegistry.get(1L, 99L)).thenReturn(ReviewSession(listOf(10L)))
        whenever(reviewManager.getPendingQuestion(1L, 10L)).thenReturn(
            pendingQuestion(guildId = 1L, userId = 10L, question = "Q2", answer = "A2", queuedAt = 20L)
        )

        command.onButtonInteraction(buttonEvent)

        verify(buttonEvent).reply("This review message is out of date. Run `/review` again.")
        verify(reviewManager, never()).approve(any(), any(), any())
    }

    @Test
    fun `starting review skips applicants that already left`() {
        stubSlashReviewStart()

        val session = ReviewSession(listOf(10L, 20L))
        whenever(reviewManager.createSession(1L)).thenReturn(session)
        whenever(reviewManager.getPendingQuestion(1L, 10L)).thenReturn(
            pendingQuestion(guildId = 1L, userId = 10L, question = "Q1", answer = "A1", queuedAt = 10L)
        )
        whenever(reviewManager.getPendingQuestion(1L, 20L)).thenReturn(
            pendingQuestion(guildId = 1L, userId = 20L, question = "Q2", answer = "A2", queuedAt = 20L)
        )
        whenever(guild.getMemberById(10L)).thenReturn(null)
        stubApplicantPresent(20L, "<@20>")

        command.onSlashCommandInteraction(slashEvent)

        val messageCaptor = argumentCaptor<String>()
        verify(slashEvent).reply(messageCaptor.capture())
        assertTrue(messageCaptor.firstValue.contains("Applicant: <@20> (`20`)"))
        verify(reviewManager).clearPendingQuestion(1L, jda, 10L)
        verify(reviewSessionRegistry).remember(eq(1L), eq(99L), any())
    }

    @Test
    fun `continuing review skips applicants that left after the current review`() {
        stubSlashReviewStart()
        stubApproveButtonInteraction()
        stubButtonEditWithActionRow()

        val session = ReviewSession(listOf(10L, 20L, 30L))
        whenever(reviewManager.createSession(1L)).thenReturn(session)
        whenever(reviewSessionRegistry.get(1L, 99L)).thenReturn(session)
        whenever(reviewManager.getPendingQuestion(1L, 10L)).thenReturn(
            pendingQuestion(guildId = 1L, userId = 10L, question = "Q1", answer = "A1", queuedAt = 10L)
        )
        whenever(reviewManager.getPendingQuestion(1L, 20L)).thenReturn(
            pendingQuestion(guildId = 1L, userId = 20L, question = "Q2", answer = "A2", queuedAt = 20L)
        )
        whenever(reviewManager.getPendingQuestion(1L, 30L)).thenReturn(
            pendingQuestion(guildId = 1L, userId = 30L, question = "Q3", answer = "A3", queuedAt = 30L)
        )
        stubApplicantPresent(10L, "<@10>")
        whenever(guild.getMemberById(20L)).thenReturn(null)
        stubApplicantPresent(30L, "<@30>")
        whenever(reviewManager.approve(eq(guild), eq(jda), eq(10L))).thenReturn("Approved <@10>.")

        command.onSlashCommandInteraction(slashEvent)
        command.onButtonInteraction(buttonEvent)

        val messageCaptor = argumentCaptor<String>()
        verify(buttonEvent).editMessage(messageCaptor.capture())
        assertTrue(messageCaptor.firstValue.contains("Applicant: <@30> (`30`)"))
        verify(reviewManager).clearPendingQuestion(1L, jda, 20L)
    }

    private fun verifyReviewLog(title: String, fieldName: String, fieldValue: String) {
        val embedCaptor = argumentCaptor<EmbedBuilder>()
        verify(guildLogger, atLeastOnce()).log(
            embedCaptor.capture(),
            anyOrNull(),
            any<Guild>(),
            anyOrNull<List<MessageEmbed>>(),
            any<GuildLogger.LogTypeAction>(),
            anyOrNull<ByteArray>()
        )
        val matchingEmbed = embedCaptor.allValues
            .map { it.build() }
            .firstOrNull { embed -> embed.title == title && embed.fields.any { it.name == fieldName && it.value == fieldValue } }

        assertTrue(matchingEmbed != null, "Expected $title log with $fieldName=$fieldValue")
    }
}
