package be.duncanc.discordmodbot.member.gate

import be.duncanc.discordmodbot.member.gate.persistence.MemberGateQuestion
import be.duncanc.discordmodbot.member.gate.persistence.MemberGateQuestionRepository
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.Member
import net.dv8tion.jda.api.entities.Role
import net.dv8tion.jda.api.entities.User
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel
import net.dv8tion.jda.api.requests.restaction.AuditableRestAction
import net.dv8tion.jda.api.requests.restaction.MessageCreateAction
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.*
import java.util.*

@ExtendWith(MockitoExtension::class)
class MemberGateReviewManagerTest {
    @Mock
    private lateinit var memberGateQuestionRepository: MemberGateQuestionRepository

    @Mock
    private lateinit var memberGateService: MemberGateService

    @Mock
    private lateinit var promptRegistry: MemberGateReviewPromptRegistry

    @Mock
    private lateinit var guild: Guild

    @Mock
    private lateinit var otherGuild: Guild

    @Mock
    private lateinit var member: Member

    @Mock
    private lateinit var user: User

    @Mock
    private lateinit var role: Role

    @Mock
    private lateinit var jda: JDA

    @Mock
    private lateinit var gateChannel: TextChannel

    @Mock
    private lateinit var addRoleAction: AuditableRestAction<Void>

    @Mock
    private lateinit var messageCreateAction: MessageCreateAction

    private lateinit var reviewManager: MemberGateReviewManager

    @BeforeEach
    fun setUp() {
        reviewManager = MemberGateReviewManager(memberGateQuestionRepository, memberGateService, promptRegistry)
    }

    private fun pendingQuestion(guildId: Long, userId: Long, queuedAt: Long, question: String, answer: String): MemberGateQuestion {
        return MemberGateQuestion(
            id = MemberGateQuestion.createId(guildId, userId),
            userId = userId,
            question = question,
            answer = answer,
            guildId = guildId,
            queuedAt = queuedAt
        )
    }

    @Test
    fun `createSession ignores null and legacy applicants and returns oldest applicants for guild first`() {
        @Suppress("UNCHECKED_CAST")
        val repositoryEntries = listOf<MemberGateQuestion?>(
            null,
            MemberGateQuestion(
                id = "legacy",
                userId = 0L,
                question = "Legacy",
                answer = "Answer",
                guildId = 0L,
                queuedAt = 1L
            ),
            pendingQuestion(guildId = 1L, userId = 30L, queuedAt = 30L, question = "Q3", answer = "A3"),
            pendingQuestion(guildId = 2L, userId = 99L, queuedAt = 5L, question = "QX", answer = "AX"),
            pendingQuestion(guildId = 1L, userId = 10L, queuedAt = 10L, question = "Q1", answer = "A1"),
            pendingQuestion(guildId = 1L, userId = 20L, queuedAt = 20L, question = "Q2", answer = "A2")
        ) as List<MemberGateQuestion>
        whenever(memberGateQuestionRepository.findAll()).thenReturn(repositoryEntries)

        val session = reviewManager.createSession(1L)

        assertEquals(10L, session?.getCurrentUserId())
        assertEquals(20L, session?.advanceAfterReview())
    }

    @Test
    fun `getPendingQuestion uses guild scoped redis id`() {
        val question = pendingQuestion(guildId = 1L, userId = 10L, queuedAt = 10L, question = "Q1", answer = "A1")
        whenever(memberGateQuestionRepository.findById(MemberGateQuestion.createId(1L, 10L))).thenReturn(Optional.of(question))

        assertEquals(question, reviewManager.getPendingQuestion(1L, 10L))
        verify(memberGateQuestionRepository).findById(MemberGateQuestion.createId(1L, 10L))
    }

    @Test
    fun `getPendingQuestion returns null when guild scoped redis id is missing`() {
        whenever(memberGateQuestionRepository.findById(MemberGateQuestion.createId(1L, 10L))).thenReturn(Optional.empty())

        assertNull(reviewManager.getPendingQuestion(1L, 10L))
    }

    @Test
    fun `savePendingQuestion creates distinct redis ids for the same user in different guilds`() {
        val otherMember = mock<Member>()

        whenever(guild.idLong).thenReturn(1L)
        whenever(otherGuild.idLong).thenReturn(2L)
        whenever(member.guild).thenReturn(guild)
        whenever(otherMember.guild).thenReturn(otherGuild)
        whenever(member.user).thenReturn(user)
        whenever(otherMember.user).thenReturn(user)
        whenever(user.idLong).thenReturn(10L)

        reviewManager.savePendingQuestion(member, "Q1", "A1")
        reviewManager.savePendingQuestion(otherMember, "Q2", "A2")

        val captor = argumentCaptor<MemberGateQuestion>()
        verify(memberGateQuestionRepository, times(2)).save(captor.capture())
        assertEquals(listOf("1:10", "2:10"), captor.allValues.map { it.id })
    }

    @Test
    fun `approve grants member role and clears applicant`() {
        val question = pendingQuestion(guildId = 1L, userId = 10L, queuedAt = 10L, question = "Q1", answer = "A1")
        whenever(memberGateQuestionRepository.findById(MemberGateQuestion.createId(1L, 10L))).thenReturn(Optional.of(question))
        whenever(guild.idLong).thenReturn(1L)
        whenever(guild.getMemberById(10L)).thenReturn(member)
        whenever(member.user).thenReturn(user)
        whenever(user.asMention).thenReturn("<@10>")
        whenever(memberGateService.getMemberRole(1L, jda)).thenReturn(role)
        whenever(guild.addRoleToMember(member, role)).thenReturn(addRoleAction)
        whenever(promptRegistry.forget(1L, 10L)).thenReturn(null)

        val result = reviewManager.approve(guild, jda, 10L)

        assertEquals("Approved <@10>.", result)
        verify(addRoleAction).queue()
        verify(memberGateQuestionRepository).deleteById(MemberGateQuestion.createId(1L, 10L))
        verify(promptRegistry).forget(1L, 10L)
    }

    @Test
    fun `reject notifies applicant and clears them from queue`() {
        val question = pendingQuestion(guildId = 1L, userId = 10L, queuedAt = 10L, question = "Q1", answer = "A1")
        whenever(memberGateQuestionRepository.findById(MemberGateQuestion.createId(1L, 10L))).thenReturn(Optional.of(question))
        whenever(guild.idLong).thenReturn(1L)
        whenever(guild.getMemberById(10L)).thenReturn(member)
        whenever(member.user).thenReturn(user)
        whenever(user.asMention).thenReturn("<@10>")
        whenever(memberGateService.getGateChannel(1L, jda)).thenReturn(gateChannel)
        whenever(gateChannel.sendMessage(any<String>())).thenReturn(messageCreateAction)
        whenever(promptRegistry.forget(1L, 10L)).thenReturn(null)

        val result = reviewManager.reject(guild, jda, 10L)

        assertEquals("Rejected <@10>. They can use `!join` to try again.", result)
        verify(gateChannel).sendMessage(any<String>())
        verify(messageCreateAction).queue(any())
        verify(memberGateQuestionRepository).deleteById(MemberGateQuestion.createId(1L, 10L))
    }

    @Test
    fun `manual action removes applicant without notifying them`() {
        val question = pendingQuestion(guildId = 1L, userId = 10L, queuedAt = 10L, question = "Q1", answer = "A1")
        whenever(memberGateQuestionRepository.findById(MemberGateQuestion.createId(1L, 10L))).thenReturn(Optional.of(question))
        whenever(guild.idLong).thenReturn(1L)
        whenever(guild.getMemberById(10L)).thenReturn(member)
        whenever(member.user).thenReturn(user)
        whenever(user.asMention).thenReturn("<@10>")
        whenever(memberGateService.getGateChannel(1L, jda)).thenReturn(gateChannel)
        whenever(promptRegistry.forget(1L, 10L)).thenReturn(null)

        val result = reviewManager.reject(guild, jda, 10L, manualAction = true)

        assertEquals("Marked <@10> for manual action and removed them from the review queue.", result)
        verify(gateChannel, never()).sendMessage(any<String>())
        verify(memberGateQuestionRepository).deleteById(MemberGateQuestion.createId(1L, 10L))
    }
}
