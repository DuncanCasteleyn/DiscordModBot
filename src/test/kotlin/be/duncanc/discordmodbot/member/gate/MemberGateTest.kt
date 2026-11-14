package be.duncanc.discordmodbot.member.gate

import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.Role
import net.dv8tion.jda.api.entities.User
import net.dv8tion.jda.api.events.guild.member.GuildMemberRoleAddEvent
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@ExtendWith(MockitoExtension::class)
class MemberGateTest {
    @Mock
    private lateinit var memberGateService: MemberGateService

    @Mock
    private lateinit var welcomeMessageService: WelcomeMessageService

    @Mock
    private lateinit var reviewManager: ReviewManager

    @Mock
    private lateinit var memberGateAttemptService: MemberGateAttemptService

    @Mock
    private lateinit var jda: JDA

    @Mock
    private lateinit var event: GuildMemberRoleAddEvent

    @Mock
    private lateinit var guild: Guild

    @Mock
    private lateinit var user: User

    @Mock
    private lateinit var memberRole: Role

    private lateinit var memberGate: MemberGate

    @BeforeEach
    fun setUp() {
        memberGate = MemberGate(memberGateService, welcomeMessageService, reviewManager, memberGateAttemptService, jda)
    }

    @Test
    fun `granting the member role clears gate attempts even when welcome messages are empty`() {
        whenever(event.guild).thenReturn(guild)
        whenever(event.user).thenReturn(user)
        whenever(user.isBot).thenReturn(false)
        whenever(guild.idLong).thenReturn(1L)
        whenever(guild.jda).thenReturn(jda)
        whenever(memberGateService.getMemberRole(1L, jda)).thenReturn(memberRole)
        whenever(event.roles).thenReturn(listOf(memberRole))
        whenever(user.idLong).thenReturn(10L)
        whenever(welcomeMessageService.getWelcomeMessages(1L)).thenReturn(emptyList())

        memberGate.onGuildMemberRoleAdd(event)

        verify(memberGateAttemptService).clearAttempts(10L)
    }

    @Test
    fun `adding an unrelated role does not clear gate attempts`() {
        whenever(event.guild).thenReturn(guild)
        whenever(event.user).thenReturn(user)
        whenever(user.isBot).thenReturn(false)
        whenever(guild.idLong).thenReturn(1L)
        whenever(guild.jda).thenReturn(jda)
        whenever(memberGateService.getMemberRole(1L, jda)).thenReturn(memberRole)
        whenever(event.roles).thenReturn(emptyList())

        memberGate.onGuildMemberRoleAdd(event)

        verify(memberGateAttemptService, never()).clearAttempts(any())
    }

    @Test
    fun `granting the member role to a bot does not clear gate attempts`() {
        whenever(event.guild).thenReturn(guild)
        whenever(event.user).thenReturn(user)
        whenever(user.isBot).thenReturn(true)
        whenever(guild.idLong).thenReturn(1L)
        whenever(guild.jda).thenReturn(jda)
        whenever(memberGateService.getMemberRole(1L, jda)).thenReturn(memberRole)

        memberGate.onGuildMemberRoleAdd(event)

        verify(memberGateAttemptService, never()).clearAttempts(any())
    }
}
