package be.duncanc.discordmodbot.moderation

import be.duncanc.discordmodbot.logging.GuildLogger
import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.entities.*
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.interactions.InteractionHook
import net.dv8tion.jda.api.interactions.commands.OptionMapping
import net.dv8tion.jda.api.requests.restaction.WebhookMessageEditAction
import net.dv8tion.jda.api.requests.restaction.interactions.ReplyCallbackAction
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Answers
import org.mockito.Mock
import org.mockito.Mockito.lenient
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
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
    private lateinit var member: Member

    @Mock
    private lateinit var user: User

    @Mock
    private lateinit var guild: Guild

    @Mock
    private lateinit var replyAction: ReplyCallbackAction

    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private lateinit var interactionHook: InteractionHook

    @Mock
    private lateinit var editAction: WebhookMessageEditAction<Message>

    @Mock
    private lateinit var userIdOption: OptionMapping

    @Mock
    private lateinit var reasonOption: OptionMapping

    @Mock
    private lateinit var muteRoleEntity: Role

    private lateinit var command: MuteByIdCommand

    @BeforeEach
    @Suppress("UNCHECKED_CAST")
    fun setUp() {
        command = MuteByIdCommand(muteRoleCommandAndEventsListenerService, muteService, guildLogger)

        lenient().whenever(slashEvent.name).thenReturn("mutebyid")
        lenient().whenever(slashEvent.member).thenReturn(member)
        lenient().whenever(member.guild).thenReturn(guild)
        lenient().whenever(member.nickname).thenReturn("Moderator")
        lenient().whenever(member.user).thenReturn(user)
        lenient().whenever(user.name).thenReturn("ModeratorUser")
        lenient().whenever(member.hasPermission(Permission.MANAGE_ROLES)).thenReturn(true)
        lenient().whenever(guild.idLong).thenReturn(1L)
        lenient().whenever(slashEvent.deferReply(true)).thenReturn(replyAction)
        lenient().whenever(replyAction.setEphemeral(true)).thenReturn(replyAction)
        lenient().doAnswer {
            val consumer = it.arguments[0] as Consumer<InteractionHook>
            consumer.accept(interactionHook)
            null
        }.whenever(replyAction).queue(any<Consumer<InteractionHook>>())
        lenient().whenever(interactionHook.editOriginal(any<String>())).thenReturn(editAction)
        lenient().whenever(slashEvent.getOption("user_id")).thenReturn(userIdOption)
        lenient().whenever(slashEvent.getOption("reason")).thenReturn(reasonOption)
        lenient().whenever(userIdOption.asLong).thenReturn(99L)
        lenient().whenever(userIdOption.asMember).thenReturn(null)
        lenient().whenever(reasonOption.asString).thenReturn("Spamming")
        lenient().whenever(muteRoleCommandAndEventsListenerService.getMuteRole(guild)).thenReturn(muteRoleEntity)
    }

    @Test
    fun `mutes a user by id and confirms the request`() {
        command.onSlashCommandInteraction(slashEvent)

        verify(muteService).muteUserById(1L, 99L)
        verify(interactionHook).editOriginal("User (ID: 99) has been muted. The mute will be applied when they rejoin.")
    }
}
