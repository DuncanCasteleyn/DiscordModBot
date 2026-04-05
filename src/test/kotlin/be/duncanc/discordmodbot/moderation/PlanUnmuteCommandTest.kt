package be.duncanc.discordmodbot.moderation

import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.Member
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.interactions.commands.OptionMapping
import net.dv8tion.jda.api.requests.restaction.interactions.ReplyCallbackAction
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.argThat
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.OffsetDateTime

@ExtendWith(MockitoExtension::class)
class PlanUnmuteCommandTest {
    @Mock
    private lateinit var unmutePlanningService: UnmutePlanningService

    @Mock
    private lateinit var slashEvent: SlashCommandInteractionEvent

    @Mock
    private lateinit var member: Member

    @Mock
    private lateinit var guild: Guild

    @Mock
    private lateinit var replyAction: ReplyCallbackAction

    @Mock
    private lateinit var userOption: OptionMapping

    @Mock
    private lateinit var daysOption: OptionMapping

    private lateinit var command: PlanUnmuteCommand

    @BeforeEach
    fun setUp() {
        command = PlanUnmuteCommand(unmutePlanningService)
    }

    @Test
    fun `plans an unmute for a user who already left the server`() {
        val unmuteDateTime = OffsetDateTime.now().plusDays(2)

        whenever(slashEvent.name).thenReturn("planunmute")
        whenever(slashEvent.member).thenReturn(member)
        whenever(slashEvent.guild).thenReturn(guild)
        whenever(member.hasPermission(Permission.MANAGE_ROLES)).thenReturn(true)
        whenever(slashEvent.getOption("user")).thenReturn(userOption)
        whenever(slashEvent.getOption("days")).thenReturn(daysOption)
        whenever(userOption.asLong).thenReturn(99L)
        whenever(daysOption.asInt).thenReturn(2)
        whenever(unmutePlanningService.planUnmute(guild, 99L, member, 2)).thenReturn(unmuteDateTime)
        whenever(slashEvent.reply(any<String>())).thenReturn(replyAction)
        whenever(replyAction.setEphemeral(true)).thenReturn(replyAction)

        command.onSlashCommandInteraction(slashEvent)

        verify(unmutePlanningService).planUnmute(guild, 99L, member, 2)
        verify(slashEvent).reply(argThat<String> { contains("<@99>") })
    }
}
