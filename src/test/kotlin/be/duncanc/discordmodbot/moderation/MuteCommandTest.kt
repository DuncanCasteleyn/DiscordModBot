package be.duncanc.discordmodbot.moderation

import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.Member
import net.dv8tion.jda.api.entities.Role
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent
import net.dv8tion.jda.api.interactions.InteractionHook
import net.dv8tion.jda.api.interactions.modals.ModalMapping
import net.dv8tion.jda.api.requests.restaction.AuditableRestAction
import net.dv8tion.jda.api.requests.restaction.interactions.ReplyCallbackAction
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.function.Consumer

@ExtendWith(MockitoExtension::class)
class MuteCommandTest {
    @Mock
    private lateinit var muteRoleCommandAndEventsListener: MuteRoleCommandAndEventsListener

    @Mock
    private lateinit var muteService: MuteService

    @Mock
    private lateinit var modalEvent: ModalInteractionEvent

    @Mock
    private lateinit var moderator: Member

    @Mock
    private lateinit var targetMember: Member

    @Mock
    private lateinit var guild: Guild

    @Mock
    private lateinit var muteRole: Role

    @Mock
    private lateinit var reasonValue: ModalMapping

    @Mock
    private lateinit var replyAction: ReplyCallbackAction

    @Mock
    private lateinit var hook: InteractionHook

    @Mock
    private lateinit var addRoleAction: AuditableRestAction<Void>

    private lateinit var command: MuteCommand


    @BeforeEach
    fun setUp() {
        command = MuteCommand(muteRoleCommandAndEventsListener, muteService)
    }

    @Test
    fun `modal submission applies the mute reason to the audit log`() {
        whenever(modalEvent.modalId).thenReturn("mute_reason:99")
        whenever(modalEvent.member).thenReturn(moderator)
        whenever(modalEvent.guild).thenReturn(guild)
        whenever(moderator.hasPermission(Permission.MANAGE_ROLES)).thenReturn(true)
        whenever(moderator.canInteract(targetMember)).thenReturn(true)
        whenever(guild.getMemberById(99L)).thenReturn(targetMember)
        whenever(modalEvent.getValue("reason")).thenReturn(reasonValue)
        whenever(reasonValue.asString).thenReturn("Spamming")
        whenever(modalEvent.deferReply(true)).thenReturn(replyAction)
        doAnswer { (consumer: Consumer<InteractionHook>) ->
            consumer.accept(hook)
            null
        }.whenever(replyAction).queue(any())
        whenever(muteRoleCommandAndEventsListener.getMuteRole(guild)).thenReturn(muteRole)
        whenever(guild.addRoleToMember(targetMember, muteRole)).thenReturn(addRoleAction)
        whenever(addRoleAction.reason("Spamming")).thenReturn(addRoleAction)

        command.onModalInteraction(modalEvent)

        verify(addRoleAction).reason("Spamming")
    }
}
