package be.duncanc.discordmodbot.member.gate

import be.duncanc.discordmodbot.logging.GuildLogger
import be.duncanc.discordmodbot.member.gate.persistence.MemberGateQuestion
import be.duncanc.discordmodbot.member.gate.persistence.ReviewInterruptConfirmation
import be.duncanc.discordmodbot.member.gate.persistence.ReviewInterruptConfirmationRepository
import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.components.actionrow.ActionRow
import net.dv8tion.jda.api.components.buttons.Button
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.Member
import net.dv8tion.jda.api.entities.MessageEmbed
import net.dv8tion.jda.api.entities.User
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent
import net.dv8tion.jda.api.requests.restaction.interactions.MessageEditCallbackAction
import net.dv8tion.jda.api.requests.restaction.interactions.ReplyCallbackAction
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.*
import java.time.Instant
import java.util.Optional

@ExtendWith(MockitoExtension::class)
class ReviewCommandTest {
    @Mock
    private lateinit var reviewManager: ReviewManager

    @Mock
    private lateinit var reviewSessionRegistry: ReviewSessionRegistry

    @Mock
    private lateinit var reviewInterruptConfirmationRepository: ReviewInterruptConfirmationRepository

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
        command = ReviewCommand(reviewManager, reviewSessionRegistry, reviewInterruptConfirmationRepository, guildLogger)
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
        stubSlashReviewCommand()
        whenever(reviewSessionRegistry.get(1L, 99L)).thenReturn(null)
        whenever(reviewSessionRegistry.getOtherSessions(1L, 99L)).thenReturn(emptyList())
    }

    private fun stubSlashReviewCommand() {
        stubCommonIdentity()
        whenever(slashEvent.name).thenReturn("review")
        whenever(slashEvent.guild).thenReturn(guild)
        whenever(slashEvent.member).thenReturn(member)
        whenever(slashEvent.user).thenReturn(user)
        whenever(slashEvent.jda).thenReturn(jda)
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

    private fun stubInterruptButtonInteraction(componentId: String) {
        stubCommonIdentity()
        whenever(buttonEvent.componentId).thenReturn(componentId)
        whenever(buttonEvent.guild).thenReturn(guild)
        whenever(buttonEvent.member).thenReturn(member)
        whenever(buttonEvent.user).thenReturn(user)
        whenever(buttonEvent.jda).thenReturn(jda)
    }

    private fun captureInterruptButtonId(index: Int): String {
        val actionRowCaptor = argumentCaptor<ActionRow>()
        verify(replyAction).addComponents(actionRowCaptor.capture())
        val button = actionRowCaptor.firstValue.components[index] as Button
        return requireNotNull(button.customId)
    }

    private fun captureSavedInterruptConfirmation(): ReviewInterruptConfirmation {
        val captor = argumentCaptor<ReviewInterruptConfirmation>()
        verify(reviewInterruptConfirmationRepository).save(captor.capture())
        return captor.firstValue
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
        whenever(reviewSessionRegistry.get(1L, 99L)).thenReturn(null, session)
        stubModeratorName()
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
        whenever(reviewSessionRegistry.get(1L, 99L)).thenReturn(null, session)
        stubModeratorName()
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
        stubModeratorName()

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
    fun `starting review again continues existing moderator session`() {
        stubSlashReviewCommand()

        val storedSession = ReviewSession(
            pendingUserIds = listOf(20L),
            oldestPendingUserId = 10L,
            approvedCount = 1
        )
        whenever(reviewSessionRegistry.get(1L, 99L)).thenReturn(storedSession)
        whenever(reviewManager.getPendingQuestion(1L, 20L)).thenReturn(
            pendingQuestion(guildId = 1L, userId = 20L, question = "Q2", answer = "A2", queuedAt = 20L)
        )
        stubApplicantPresent(20L, "<@20>")

        command.onSlashCommandInteraction(slashEvent)

        val messageCaptor = argumentCaptor<String>()
        verify(slashEvent).reply(messageCaptor.capture())
        assertTrue(messageCaptor.firstValue.contains("Applicant: <@20> (`20`)"))
        assertTrue(messageCaptor.firstValue.contains("Continuing with the next pending applicant."))
        verify(reviewManager, never()).createSession(any())
        verify(reviewSessionRegistry, never()).forgetOtherSessions(any(), any())
        verify(reviewSessionRegistry).remember(eq(1L), eq(99L), same(storedSession))
        verify(guildLogger, never()).log(
            any<EmbedBuilder>(),
            anyOrNull(),
            any<Guild>(),
            anyOrNull<List<MessageEmbed>>(),
            any<GuildLogger.LogTypeAction>(),
            anyOrNull<ByteArray>()
        )
    }

    @Test
    fun `starting review with stale stored session starts a new session for newer applicants`() {
        stubSlashReviewCommand()
        stubModeratorName()

        val storedSession = ReviewSession(listOf(20L))
        val newSession = ReviewSession(listOf(30L))
        whenever(reviewSessionRegistry.get(1L, 99L)).thenReturn(storedSession)
        whenever(reviewSessionRegistry.getOtherSessions(1L, 99L)).thenReturn(emptyList())
        whenever(reviewManager.createSession(1L)).thenReturn(newSession)
        whenever(reviewManager.getPendingQuestion(1L, 20L)).thenReturn(null)
        whenever(reviewManager.getPendingQuestion(1L, 30L)).thenReturn(
            pendingQuestion(guildId = 1L, userId = 30L, question = "Q3", answer = "A3", queuedAt = 30L)
        )
        stubApplicantPresent(30L, "<@30>")

        command.onSlashCommandInteraction(slashEvent)

        val messageCaptor = argumentCaptor<String>()
        verify(slashEvent).reply(messageCaptor.capture())
        assertTrue(messageCaptor.firstValue.contains("Applicant: <@30> (`30`)"))
        verify(reviewSessionRegistry).forget(1L, 99L)
        verify(reviewManager).createSession(1L)
        verify(reviewSessionRegistry).remember(eq(1L), eq(99L), same(newSession))
    }

    @Test
    fun `starting review prompts before interrupting another moderator's unfinished session`() {
        stubSlashReviewStart()

        val interruptedSession = ReviewSession(
            pendingUserIds = listOf(50L),
            approvedCount = 2,
            rejectedCount = 1,
            manualActionCount = 3
        )
        whenever(reviewSessionRegistry.getOtherSessions(1L, 99L)).thenReturn(
            listOf(
                ReviewSessionRegistry.StoredReviewSession(
                    reviewerId = 42L,
                    session = interruptedSession,
                    updatedAt = Instant.parse("2026-06-28T12:00:00Z")
                )
            )
        )
        val session = ReviewSession(listOf(10L))
        whenever(reviewManager.createSession(1L)).thenReturn(session)
        whenever(reviewManager.getPendingQuestion(1L, 10L)).thenReturn(
            pendingQuestion(guildId = 1L, userId = 10L, question = "Q1", answer = "A1", queuedAt = 10L)
        )
        whenever(guild.getMemberById(10L)).thenReturn(mock())

        command.onSlashCommandInteraction(slashEvent)

        val messageCaptor = argumentCaptor<String>()
        verify(slashEvent).reply(messageCaptor.capture())
        assertTrue(messageCaptor.firstValue.contains("Another moderator is already reviewing members:"))
        assertTrue(messageCaptor.firstValue.contains("<@42> has 1 pending applicant(s), last updated <t:1782648000:R>."))
        val confirmation = captureSavedInterruptConfirmation()
        assertEquals(1L, confirmation.guildId)
        assertEquals(99L, confirmation.reviewerId)
        assertEquals(setOf(42L), confirmation.targetReviewerIds)
        assertTrue(captureInterruptButtonId(0).length <= Button.ID_MAX_LENGTH)
        assertTrue(captureInterruptButtonId(1).length <= Button.ID_MAX_LENGTH)
        verify(reviewSessionRegistry, never()).forgetOtherSessions(any(), any())
        verify(reviewSessionRegistry, never()).remember(eq(1L), eq(99L), same(session))
    }

    @Test
    fun `confirming interruption logs and removes another moderator's unfinished session`() {
        stubInterruptButtonInteraction("member-gate-review-interrupt:confirm:token")
        stubModeratorName()
        stubButtonEditWithActionRow()

        val interruptedSession = ReviewSession(
            pendingUserIds = listOf(50L),
            approvedCount = 2,
            rejectedCount = 1,
            manualActionCount = 3
        )
        val storedSession = ReviewSessionRegistry.StoredReviewSession(
            reviewerId = 42L,
            session = interruptedSession,
            updatedAt = Instant.parse("2026-06-28T12:00:00Z")
        )
        whenever(reviewInterruptConfirmationRepository.findById("token")).thenReturn(
            Optional.of(
                ReviewInterruptConfirmation(
                    id = "token",
                    guildId = 1L,
                    reviewerId = 99L,
                    targetReviewerIds = setOf(42L)
                )
            )
        )
        whenever(reviewSessionRegistry.getOtherSessions(1L, 99L)).thenReturn(listOf(storedSession))
        whenever(reviewSessionRegistry.forgetSessions(1L, setOf(42L))).thenReturn(listOf(storedSession))

        val session = ReviewSession(listOf(10L))
        whenever(reviewManager.createSession(1L)).thenReturn(session)
        whenever(reviewManager.getPendingQuestion(1L, 10L)).thenReturn(
            pendingQuestion(guildId = 1L, userId = 10L, question = "Q1", answer = "A1", queuedAt = 10L)
        )
        whenever(guild.getMemberById(42L)).thenReturn(null)
        stubApplicantPresent(10L, "<@10>")

        command.onButtonInteraction(buttonEvent)

        verifyReviewLog("Member gate review interrupted", "Approved", "2")
        verifyReviewLog("Member gate review interrupted", "Rejected", "1")
        verifyReviewLog("Member gate review interrupted", "Manual action", "3")
        verify(reviewInterruptConfirmationRepository).deleteById("token")
        verify(reviewSessionRegistry).remember(eq(1L), eq(99L), same(session))
        val messageCaptor = argumentCaptor<String>()
        verify(buttonEvent).editMessage(messageCaptor.capture())
        assertTrue(messageCaptor.firstValue.contains("Applicant: <@10> (`10`)"))
    }

    @Test
    fun `confirming stale interruption asks moderator to review current sessions`() {
        whenever(guild.idLong).thenReturn(1L)
        whenever(user.idLong).thenReturn(99L)
        whenever(member.hasPermission(Permission.MANAGE_ROLES)).thenReturn(true)
        whenever(buttonEvent.componentId).thenReturn("member-gate-review-interrupt:confirm:token")
        whenever(buttonEvent.guild).thenReturn(guild)
        whenever(buttonEvent.member).thenReturn(member)
        whenever(buttonEvent.user).thenReturn(user)
        stubButtonEditWithList()
        whenever(reviewInterruptConfirmationRepository.findById("token")).thenReturn(
            Optional.of(
                ReviewInterruptConfirmation(
                    id = "token",
                    guildId = 1L,
                    reviewerId = 99L,
                    targetReviewerIds = setOf(42L)
                )
            )
        )

        whenever(reviewSessionRegistry.getOtherSessions(1L, 99L)).thenReturn(
            listOf(
                ReviewSessionRegistry.StoredReviewSession(
                    reviewerId = 43L,
                    session = ReviewSession(listOf(50L)),
                    updatedAt = Instant.parse("2026-06-28T12:00:00Z")
                )
            )
        )

        command.onButtonInteraction(buttonEvent)

        verify(buttonEvent).editMessage("The active review sessions changed. Run `/review` again to confirm the current sessions.")
        verify(reviewInterruptConfirmationRepository).deleteById("token")
        verify(reviewSessionRegistry, never()).forgetSessions(any(), any())
        verify(reviewManager, never()).createSession(any())
    }

    @Test
    fun `confirming interruption deletes only sessions shown in prompt`() {
        stubInterruptButtonInteraction("member-gate-review-interrupt:confirm:token")
        stubModeratorName()
        stubButtonEditWithActionRow()

        val storedSessions = listOf(
            ReviewSessionRegistry.StoredReviewSession(
                reviewerId = 42L,
                session = ReviewSession(listOf(50L)),
                updatedAt = Instant.parse("2026-06-28T12:00:00Z")
            ),
            ReviewSessionRegistry.StoredReviewSession(
                reviewerId = 43L,
                session = ReviewSession(listOf(60L)),
                updatedAt = Instant.parse("2026-06-28T12:00:00Z")
            )
        )
        whenever(reviewInterruptConfirmationRepository.findById("token")).thenReturn(
            Optional.of(
                ReviewInterruptConfirmation(
                    id = "token",
                    guildId = 1L,
                    reviewerId = 99L,
                    targetReviewerIds = setOf(42L, 43L)
                )
            )
        )
        whenever(reviewSessionRegistry.getOtherSessions(1L, 99L)).thenReturn(storedSessions)
        whenever(reviewSessionRegistry.forgetSessions(1L, setOf(42L, 43L))).thenReturn(storedSessions)
        val session = ReviewSession(listOf(10L))
        whenever(reviewManager.createSession(1L)).thenReturn(session)
        whenever(reviewManager.getPendingQuestion(1L, 10L)).thenReturn(
            pendingQuestion(guildId = 1L, userId = 10L, question = "Q1", answer = "A1", queuedAt = 10L)
        )
        whenever(guild.getMemberById(42L)).thenReturn(null)
        whenever(guild.getMemberById(43L)).thenReturn(null)
        stubApplicantPresent(10L, "<@10>")

        command.onButtonInteraction(buttonEvent)

        verify(reviewSessionRegistry).forgetSessions(1L, setOf(42L, 43L))
        verify(reviewSessionRegistry, never()).forgetOtherSessions(any(), any())
    }

    @Test
    fun `cancelling interruption keeps another moderator's unfinished session`() {
        whenever(guild.idLong).thenReturn(1L)
        whenever(user.idLong).thenReturn(99L)
        whenever(member.hasPermission(Permission.MANAGE_ROLES)).thenReturn(true)
        whenever(buttonEvent.componentId).thenReturn("member-gate-review-interrupt:cancel:token")
        whenever(buttonEvent.guild).thenReturn(guild)
        whenever(buttonEvent.member).thenReturn(member)
        whenever(buttonEvent.user).thenReturn(user)
        stubButtonEditWithList()
        whenever(reviewInterruptConfirmationRepository.findById("token")).thenReturn(
            Optional.of(
                ReviewInterruptConfirmation(
                    id = "token",
                    guildId = 1L,
                    reviewerId = 99L,
                    targetReviewerIds = setOf(42L)
                )
            )
        )

        command.onButtonInteraction(buttonEvent)

        verify(buttonEvent).editMessage("Review start cancelled. The other moderator's review session was not interrupted.")
        verify(reviewInterruptConfirmationRepository).deleteById("token")
        verify(reviewSessionRegistry, never()).forgetOtherSessions(any(), any())
        verify(reviewManager, never()).createSession(any())
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
    fun `button advances when current applicant left before review action`() {
        stubApproveButtonInteraction()
        stubButtonEditWithActionRow()

        val session = ReviewSession(listOf(10L, 20L))
        whenever(reviewSessionRegistry.get(1L, 99L)).thenReturn(session)
        whenever(reviewManager.getPendingQuestion(1L, 10L)).thenReturn(null)
        whenever(reviewManager.getPendingQuestion(1L, 20L)).thenReturn(
            pendingQuestion(guildId = 1L, userId = 20L, question = "Q2", answer = "A2", queuedAt = 20L)
        )
        stubApplicantPresent(20L, "<@20>")

        command.onButtonInteraction(buttonEvent)

        val messageCaptor = argumentCaptor<String>()
        verify(buttonEvent).editMessage(messageCaptor.capture())
        val editedMessage = messageCaptor.firstValue
        assertTrue(editedMessage.contains("The applicant left; no further action is needed."))
        assertTrue(editedMessage.contains("Applicant: <@20> (`20`)"))
        verify(reviewSessionRegistry).remember(eq(1L), eq(99L), any())
        verify(reviewManager, never()).approve(any(), any(), any())
    }

    @Test
    fun `button completes review when final current applicant already left`() {
        stubApproveButtonInteraction()
        stubModeratorName()
        stubButtonEditWithList()

        val session = ReviewSession(listOf(10L), approvedCount = 2, rejectedCount = 1, manualActionCount = 1)
        whenever(reviewSessionRegistry.get(1L, 99L)).thenReturn(session)
        whenever(reviewManager.getPendingQuestion(1L, 10L)).thenReturn(null)

        command.onButtonInteraction(buttonEvent)

        val messageCaptor = argumentCaptor<String>()
        verify(buttonEvent).editMessage(messageCaptor.capture())
        val editedMessage = messageCaptor.firstValue
        assertTrue(editedMessage.contains("The applicant left; no further action is needed."))
        assertTrue(editedMessage.contains("There are no more pending applicants in the queue."))
        verify(reviewSessionRegistry).forget(1L, 99L)
        verifyReviewLog("Member gate review completed", "Approved", "2")
        verify(reviewManager, never()).approve(any(), any(), any())
    }

    @Test
    fun `starting review skips applicants that already left`() {
        stubSlashReviewStart()

        val session = ReviewSession(listOf(10L, 20L))
        whenever(reviewManager.createSession(1L)).thenReturn(session)
        stubModeratorName()
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
        stubModeratorName()
        stubButtonEditWithActionRow()

        val session = ReviewSession(listOf(10L, 20L, 30L))
        whenever(reviewManager.createSession(1L)).thenReturn(session)
        whenever(reviewSessionRegistry.get(1L, 99L)).thenReturn(null, session)
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
        val embed = requireNotNull(matchingEmbed)
        assertTrue(embed.fields.none { it.name == "UUID" }, "Expected $title log without UUID field")
        assertTrue(
            embed.fields.any { it.name == "Moderator" && !it.isInline },
            "Expected $title log with non-inline Moderator field"
        )
    }
}
