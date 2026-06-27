package be.duncanc.discordmodbot.moderation

import be.duncanc.discordmodbot.logging.GuildLogger
import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.entities.*
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.interactions.InteractionHook
import net.dv8tion.jda.api.interactions.commands.OptionMapping
import net.dv8tion.jda.api.interactions.modals.ModalMapping
import net.dv8tion.jda.api.requests.restaction.WebhookMessageEditAction
import net.dv8tion.jda.api.requests.restaction.interactions.ModalCallbackAction
import net.dv8tion.jda.api.requests.restaction.interactions.ReplyCallbackAction
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Answers
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.function.Consumer

@ExtendWith(MockitoExtension::class)
class MuteByIdCommandTest {
    @Mock
    private lateinit var muteRoleCommandAndEventsListenerService: MuteRoleCommandAndEventsListener

    @Mock
    private lateinit var muteService: MuteService

    @Mock
    private lateinit var guildLogger: GuildLogger

    @Mock
    private lateinit var slashEvent: SlashCommandInteractionEvent

    @Mock
    private lateinit var modalEvent: ModalInteractionEvent

    @Mock
    private lateinit var member: Member

    @Mock
    private lateinit var user: User

    @Mock
    private lateinit var guild: Guild

    @Mock
    private lateinit var replyAction: ReplyCallbackAction

    @Mock
    private lateinit var modalAction: ModalCallbackAction

    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private lateinit var interactionHook: InteractionHook

    @Mock
    private lateinit var editAction: WebhookMessageEditAction<Message>

    @Mock
    private lateinit var userIdOption: OptionMapping

    @Mock
    private lateinit var reasonValue: ModalMapping

    @Mock
    private lateinit var muteRoleEntity: Role

    private lateinit var command: MuteByIdCommand

    @BeforeEach
    fun setUp() {
        command = MuteByIdCommand(muteRoleCommandAndEventsListenerService, muteService, guildLogger)
    }

    @Test
    fun `slash command opens a reason modal`() {
        whenever(slashEvent.name).thenReturn("mutebyid")
        whenever(slashEvent.member).thenReturn(member)
        whenever(member.hasPermission(Permission.MANAGE_ROLES)).thenReturn(true)
        whenever(slashEvent.getOption("user_id")).thenReturn(userIdOption)
        whenever(userIdOption.asLong).thenReturn(99L)
        whenever(slashEvent.replyModal(any())).thenReturn(modalAction)

        command.onSlashCommandInteraction(slashEvent)

        verify(slashEvent).replyModal(org.mockito.kotlin.argThat {
            id == "mutebyid_reason:99" &&
                    components.size == 2 &&
                    components[0].asTextDisplay().content == "Muting: <@99> (ID: 99)"
        })
    }

    @Test
    fun `modal submission mutes a user by id and confirms the request`() {
        whenever(modalEvent.modalId).thenReturn("mutebyid_reason:99")
        whenever(modalEvent.member).thenReturn(member)
        whenever(member.guild).thenReturn(guild)
        whenever(member.nickname).thenReturn("Moderator")
        whenever(member.user).thenReturn(user)
        whenever(user.name).thenReturn("ModeratorUser")
        whenever(member.hasPermission(Permission.MANAGE_ROLES)).thenReturn(true)
        whenever(guild.idLong).thenReturn(1L)
        whenever(guild.getMemberById(99L)).thenReturn(null)
        whenever(modalEvent.getValue("reason")).thenReturn(reasonValue)
        whenever(reasonValue.asString).thenReturn("Spamming")
        whenever(modalEvent.deferReply(true)).thenReturn(replyAction)
        doAnswer { (consumer: Consumer<InteractionHook>) ->
            consumer.accept(interactionHook)
            null
        }.whenever(replyAction).queue(any())
        whenever(interactionHook.editOriginal(any<String>())).thenReturn(editAction)
        whenever(muteRoleCommandAndEventsListenerService.getMuteRole(guild)).thenReturn(muteRoleEntity)

        command.onModalInteraction(modalEvent)

        verify(muteService).muteUserById(1L, 99L)
        verify(interactionHook).editOriginal("User (ID: 99) has been muted. The mute will be applied when they rejoin.")
    }
}
