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
import org.mockito.kotlin.any
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
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

    @Test
    fun `createSession ignores null and legacy applicants and returns oldest applicants for guild first`() {
        @Suppress("UNCHECKED_CAST")
        val repositoryEntries = listOf<MemberGateQuestion?>(
            null,
            MemberGateQuestion(77L, "Legacy", "Answer", guildId = 0L, queuedAt = 1L),
            MemberGateQuestion(30L, "Q3", "A3", guildId = 1L, queuedAt = 30L),
            MemberGateQuestion(99L, "QX", "AX", guildId = 2L, queuedAt = 5L),
            MemberGateQuestion(10L, "Q1", "A1", guildId = 1L, queuedAt = 10L),
            MemberGateQuestion(20L, "Q2", "A2", guildId = 1L, queuedAt = 20L)
        ) as List<MemberGateQuestion>
        whenever(memberGateQuestionRepository.findAll()).thenReturn(repositoryEntries)

        val session = reviewManager.createSession(1L)

        assertEquals(10L, session?.getCurrentUserId())
        assertEquals(20L, session?.skipCurrent())
    }

    @Test
    fun `getPendingQuestion ignores applicants from another guild`() {
        whenever(memberGateQuestionRepository.findById(10L)).thenReturn(
            Optional.of(MemberGateQuestion(10L, "Q1", "A1", guildId = 2L, queuedAt = 10L))
        )

        assertNull(reviewManager.getPendingQuestion(1L, 10L))
    }

    @Test
    fun `getPendingQuestion ignores legacy applicants without guild id`() {
        whenever(memberGateQuestionRepository.findById(10L)).thenReturn(
            Optional.of(MemberGateQuestion(10L, "Q1", "A1", guildId = 0L, queuedAt = 10L))
        )

        assertNull(reviewManager.getPendingQuestion(1L, 10L))
    }

    @Test
    fun `approve grants member role and clears applicant`() {
        val question = MemberGateQuestion(10L, "Q1", "A1", guildId = 1L, queuedAt = 10L)
        whenever(memberGateQuestionRepository.findById(10L)).thenReturn(Optional.of(question))
        whenever(guild.idLong).thenReturn(1L)
        whenever(guild.getMemberById(10L)).thenReturn(member)
        whenever(member.user).thenReturn(user)
        whenever(user.asMention).thenReturn("<@10>")
        whenever(memberGateService.getMemberRole(1L, jda)).thenReturn(role)
        whenever(guild.addRoleToMember(member, role)).thenReturn(addRoleAction)
        whenever(promptRegistry.forget(10L)).thenReturn(null)

        val result = reviewManager.approve(guild, jda, 10L)

        assertEquals("Approved <@10>.", result)
        verify(addRoleAction).queue()
        verify(memberGateQuestionRepository).deleteById(10L)
        verify(promptRegistry).forget(10L)
    }

    @Test
    fun `reject notifies applicant and clears them from queue`() {
        val question = MemberGateQuestion(10L, "Q1", "A1", guildId = 1L, queuedAt = 10L)
        whenever(memberGateQuestionRepository.findById(10L)).thenReturn(Optional.of(question))
        whenever(guild.idLong).thenReturn(1L)
        whenever(guild.getMemberById(10L)).thenReturn(member)
        whenever(member.user).thenReturn(user)
        whenever(user.asMention).thenReturn("<@10>")
        whenever(memberGateService.getGateChannel(1L, jda)).thenReturn(gateChannel)
        whenever(gateChannel.sendMessage(any<String>())).thenReturn(messageCreateAction)
        whenever(promptRegistry.forget(10L)).thenReturn(null)

        val result = reviewManager.reject(guild, jda, 10L)

        assertEquals("Rejected <@10>. They can use `!join` to try again.", result)
        verify(gateChannel).sendMessage(any<String>())
        verify(messageCreateAction).queue(any())
        verify(memberGateQuestionRepository).deleteById(10L)
    }

    @Test
    fun `manual action removes applicant without notifying them`() {
        val question = MemberGateQuestion(10L, "Q1", "A1", guildId = 1L, queuedAt = 10L)
        whenever(memberGateQuestionRepository.findById(10L)).thenReturn(Optional.of(question))
        whenever(guild.idLong).thenReturn(1L)
        whenever(guild.getMemberById(10L)).thenReturn(member)
        whenever(member.user).thenReturn(user)
        whenever(user.asMention).thenReturn("<@10>")
        whenever(memberGateService.getGateChannel(1L, jda)).thenReturn(gateChannel)
        whenever(promptRegistry.forget(10L)).thenReturn(null)

        val result = reviewManager.reject(guild, jda, 10L, manualAction = true)

        assertEquals("Marked <@10> for manual action and removed them from the review queue.", result)
        verify(gateChannel, never()).sendMessage(any<String>())
        verify(memberGateQuestionRepository).deleteById(10L)
    }
}
