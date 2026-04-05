package be.duncanc.discordmodbot.moderation

import be.duncanc.discordmodbot.moderation.persistence.GuildWarnPointsSettingsRepository
import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.entities.Member
import net.dv8tion.jda.api.entities.User
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent
import net.dv8tion.jda.api.interactions.modals.ModalMapping
import net.dv8tion.jda.api.requests.restaction.interactions.ModalCallbackAction
import net.dv8tion.jda.api.requests.restaction.interactions.ReplyCallbackAction
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.argThat
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.OffsetDateTime

@ExtendWith(MockitoExtension::class)
class AddWarnPointsCommandTest {
    @Mock
    private lateinit var guildWarnPointsService: GuildWarnPointsService

    @Mock
    private lateinit var guildWarnPointsSettingsRepository: GuildWarnPointsSettingsRepository

    @Mock
    private lateinit var muteRoleCommandAndEventsListener: MuteRoleCommandAndEventsListener

    @Mock
    private lateinit var unmutePlanningService: UnmutePlanningService

    @Mock
    private lateinit var buttonEvent: ButtonInteractionEvent

    @Mock
    private lateinit var modalEvent: ModalInteractionEvent

    @Mock
    private lateinit var member: Member

    @Mock
    private lateinit var moderatorUser: User

    @Mock
    private lateinit var replyAction: ReplyCallbackAction

    @Mock
    private lateinit var modalAction: ModalCallbackAction

    @Mock
    private lateinit var guild: net.dv8tion.jda.api.entities.Guild

    @Mock
    private lateinit var targetMember: Member

    @Mock
    private lateinit var daysValue: ModalMapping

    private lateinit var command: AddWarnPointsCommand

    @BeforeEach
    fun setUp() {
        command = AddWarnPointsCommand(
            guildWarnPointsService,
            guildWarnPointsSettingsRepository,
            muteRoleCommandAndEventsListener,
            unmutePlanningService
        )
    }

    @Test
    fun `plan unmute button only works for the moderator who triggered it`() {
        whenever(buttonEvent.componentId).thenReturn("addwarnpoints_unmute:12:99")
        whenever(buttonEvent.member).thenReturn(member)
        whenever(member.hasPermission(Permission.MANAGE_ROLES)).thenReturn(true)
        whenever(buttonEvent.user).thenReturn(moderatorUser)
        whenever(moderatorUser.idLong).thenReturn(42L)
        whenever(buttonEvent.reply(any<String>())).thenReturn(replyAction)
        whenever(replyAction.setEphemeral(true)).thenReturn(replyAction)

        command.onButtonInteraction(buttonEvent)

        verify(buttonEvent).reply("You cannot plan an unmute initiated by another moderator.")
        verify(buttonEvent, never()).replyModal(any())
    }

    @Test
    fun `plan unmute button opens a modal for the moderator who triggered it`() {
        whenever(buttonEvent.componentId).thenReturn("addwarnpoints_unmute:12:99")
        whenever(buttonEvent.member).thenReturn(member)
        whenever(member.hasPermission(Permission.MANAGE_ROLES)).thenReturn(true)
        whenever(buttonEvent.user).thenReturn(moderatorUser)
        whenever(moderatorUser.idLong).thenReturn(12L)
        whenever(buttonEvent.replyModal(any())).thenReturn(modalAction)

        command.onButtonInteraction(buttonEvent)

        verify(buttonEvent).replyModal(argThat { id == "addwarnpoints_plan_unmute:12:99" })
    }

    @Test
    fun `plan unmute modal schedules the unmute`() {
        val unmuteDateTime = OffsetDateTime.now().plusDays(3)

        whenever(modalEvent.modalId).thenReturn("addwarnpoints_plan_unmute:12:99")
        whenever(modalEvent.member).thenReturn(member)
        whenever(member.hasPermission(Permission.MANAGE_ROLES)).thenReturn(true)
        whenever(modalEvent.user).thenReturn(moderatorUser)
        whenever(moderatorUser.idLong).thenReturn(12L)
        whenever(modalEvent.getValue("unmute_days")).thenReturn(daysValue)
        whenever(daysValue.asString).thenReturn("3")
        whenever(modalEvent.guild).thenReturn(guild)
        whenever(unmutePlanningService.planUnmute(guild, 99L, member, 3)).thenReturn(unmuteDateTime)
        whenever(guild.getMemberById(99L)).thenReturn(targetMember)
        whenever(targetMember.asMention).thenReturn("<@99>")
        whenever(modalEvent.reply(any<String>())).thenReturn(replyAction)
        whenever(replyAction.setEphemeral(true)).thenReturn(replyAction)

        command.onModalInteraction(modalEvent)

        verify(unmutePlanningService).planUnmute(guild, 99L, member, 3)
        verify(modalEvent).reply(argThat<String> { contains("Unmute has been planned for <@99>") })
    }

    @Test
    fun `skip unmute button only works for the moderator who triggered it`() {
        whenever(buttonEvent.componentId).thenReturn("addwarnpoints_skip_unmute:12:99")
        whenever(buttonEvent.user).thenReturn(moderatorUser)
        whenever(moderatorUser.idLong).thenReturn(77L)
        whenever(buttonEvent.reply(any<String>())).thenReturn(replyAction)
        whenever(replyAction.setEphemeral(true)).thenReturn(replyAction)

        command.onButtonInteraction(buttonEvent)

        verify(buttonEvent).reply("You cannot skip an unmute prompt initiated by another moderator.")
        verify(buttonEvent, never()).editMessage(any<String>())
    }
}
