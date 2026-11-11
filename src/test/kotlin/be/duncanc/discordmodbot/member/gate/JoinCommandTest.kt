package be.duncanc.discordmodbot.member.gate

import be.duncanc.discordmodbot.logging.GuildLogger
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.Member
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel
import net.dv8tion.jda.api.entities.channel.unions.MessageChannelUnion
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent
import net.dv8tion.jda.api.interactions.modals.ModalMapping
import net.dv8tion.jda.api.requests.restaction.MessageCreateAction
import net.dv8tion.jda.api.requests.restaction.interactions.ReplyCallbackAction
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@ExtendWith(MockitoExtension::class)
class JoinCommandTest {
    @Mock
    private lateinit var memberGateService: MemberGateService

    @Mock
    private lateinit var reviewManager: ReviewManager

    @Mock
    private lateinit var guildLogger: GuildLogger

    @Mock
    private lateinit var memberGateAttemptService: MemberGateAttemptService

    @Mock
    private lateinit var modalEvent: ModalInteractionEvent

    @Mock
    private lateinit var member: Member

    @Mock
    private lateinit var guild: Guild

    @Mock
    private lateinit var rulesValue: ModalMapping

    @Mock
    private lateinit var answerValue: ModalMapping

    @Mock(extraInterfaces = [MessageChannelUnion::class])
    private lateinit var channel: TextChannel

    @Mock
    private lateinit var messageCreateAction: MessageCreateAction

    @Mock
    private lateinit var replyAction: ReplyCallbackAction

    private lateinit var joinCommand: JoinCommand

    @BeforeEach
    fun setUp() {
        joinCommand = JoinCommand(memberGateService, reviewManager, guildLogger, memberGateAttemptService)
    }

    private fun stubValidAgreeingSubmission() {
        whenever(modalEvent.modalId).thenReturn("join-modal")
        whenever(modalEvent.member).thenReturn(member)
        whenever(modalEvent.getValue("rules-answer")).thenReturn(rulesValue)
        whenever(rulesValue.asStringList).thenReturn(listOf("agree-rules"))
        whenever(member.guild).thenReturn(guild)
        whenever(guild.idLong).thenReturn(1L)
        whenever(member.idLong).thenReturn(10L)
        whenever(modalEvent.getValue("join-answer")).thenReturn(answerValue)
        whenever(answerValue.asString).thenReturn("My answer")
    }

    @Test
    fun `submitting a valid answer records a gate attempt`() {
        stubValidAgreeingSubmission()
        whenever(memberGateService.getModalQuestion(1L, 10L)).thenReturn("Question?")
        doReturn(channel).whenever(modalEvent).channel
        whenever(channel.sendMessage(any<String>())).thenReturn(messageCreateAction)
        whenever(modalEvent.reply(any<String>())).thenReturn(replyAction)
        whenever(replyAction.setEphemeral(true)).thenReturn(replyAction)

        joinCommand.onModalInteraction(modalEvent)

        verify(memberGateAttemptService).recordAttempt(10L)
        verify(reviewManager).savePendingQuestion(member, "Question?", "My answer")
    }

    @Test
    fun `submitting without a stored modal question does not record a gate attempt`() {
        stubValidAgreeingSubmission()
        whenever(memberGateService.getModalQuestion(1L, 10L)).thenReturn(null)
        whenever(modalEvent.reply(any<String>())).thenReturn(replyAction)
        whenever(replyAction.setEphemeral(true)).thenReturn(replyAction)

        joinCommand.onModalInteraction(modalEvent)

        verify(memberGateAttemptService, never()).recordAttempt(any())
        verify(reviewManager, never()).savePendingQuestion(any(), any(), any())
    }

    @Test
    fun `disagreeing with the rules does not record a gate attempt`() {
        whenever(modalEvent.modalId).thenReturn("join-modal")
        whenever(modalEvent.member).thenReturn(member)
        whenever(modalEvent.getValue("rules-answer")).thenReturn(rulesValue)
        whenever(rulesValue.asStringList).thenReturn(listOf("kick-me"))

        joinCommand.onModalInteraction(modalEvent)

        verify(memberGateAttemptService, never()).recordAttempt(any())
        verify(reviewManager, never()).savePendingQuestion(any(), any(), any())
    }
}
