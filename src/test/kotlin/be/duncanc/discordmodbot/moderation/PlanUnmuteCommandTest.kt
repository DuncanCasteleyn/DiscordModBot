package be.duncanc.discordmodbot.moderation

import be.duncanc.discordmodbot.logging.GuildLogger
import be.duncanc.discordmodbot.moderation.persistence.MuteRole
import be.duncanc.discordmodbot.moderation.persistence.MuteRolesRepository
import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.Member
import net.dv8tion.jda.api.entities.Role
import net.dv8tion.jda.api.entities.User
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.interactions.commands.OptionMapping
import net.dv8tion.jda.api.requests.restaction.interactions.ReplyCallbackAction
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.Mockito.lenient
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.argThat
import org.mockito.kotlin.eq
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.OffsetDateTime
import java.util.Optional

@ExtendWith(MockitoExtension::class)
class PlanUnmuteCommandTest {
    @Mock
    private lateinit var muteService: MuteService

    @Mock
    private lateinit var scheduledUnmuteService: ScheduledUnmuteService

    @Mock
    private lateinit var guildLogger: GuildLogger

    @Mock
    private lateinit var muteRolesRepository: MuteRolesRepository

    @Mock
    private lateinit var slashEvent: SlashCommandInteractionEvent

    @Mock
    private lateinit var member: Member

    @Mock
    private lateinit var moderatorUser: User

    @Mock
    private lateinit var guild: Guild

    @Mock
    private lateinit var replyAction: ReplyCallbackAction

    @Mock
    private lateinit var userOption: OptionMapping

    @Mock
    private lateinit var daysOption: OptionMapping

    @Mock
    private lateinit var muteRole: Role

    @Mock
    private lateinit var targetUser: User

    private lateinit var command: PlanUnmuteCommand

    @BeforeEach
    fun setUp() {
        command = PlanUnmuteCommand(muteService, scheduledUnmuteService, guildLogger, muteRolesRepository)

        lenient().whenever(slashEvent.name).thenReturn("planunmute")
        lenient().whenever(slashEvent.member).thenReturn(member)
        lenient().whenever(slashEvent.guild).thenReturn(guild)
        lenient().whenever(member.hasPermission(Permission.MANAGE_ROLES)).thenReturn(true)
        lenient().whenever(member.user).thenReturn(moderatorUser)
        lenient().whenever(moderatorUser.name).thenReturn("Moderator")
        lenient().whenever(guild.idLong).thenReturn(1L)
        lenient().whenever(slashEvent.getOption("user")).thenReturn(userOption)
        lenient().whenever(slashEvent.getOption("days")).thenReturn(daysOption)
        lenient().whenever(userOption.asUser).thenReturn(targetUser)
        lenient().whenever(userOption.asLong).thenReturn(99L)
        lenient().whenever(userOption.asMember).thenReturn(null)
        lenient().whenever(targetUser.name).thenReturn("TargetUser")
        lenient().whenever(daysOption.asInt).thenReturn(2)
        lenient().whenever(muteRolesRepository.findById(1L)).thenReturn(Optional.of(MuteRole(1L, 2L)))
        lenient().whenever(guild.getRoleById(2L)).thenReturn(muteRole)
        lenient().whenever(muteService.isUserMuted(1L, 99L)).thenReturn(true)
        lenient().whenever(slashEvent.reply(any<String>())).thenReturn(replyAction)
        lenient().whenever(replyAction.setEphemeral(true)).thenReturn(replyAction)
    }

    @Test
    fun `plans an unmute for a user who already left the server`() {
        command.onSlashCommandInteraction(slashEvent)

        verify(muteService).isUserMuted(1L, 99L)
        verify(scheduledUnmuteService).planUnmute(
            eq(1L),
            eq(99L),
            argThat<OffsetDateTime> { isAfter(OffsetDateTime.now().plusDays(1)) }
        )
        verify(slashEvent).reply(argThat<String> { contains("<@99>") })
    }
}
