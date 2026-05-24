package be.duncanc.discordmodbot.moderation

import be.duncanc.discordmodbot.logging.GuildLogger
import be.duncanc.discordmodbot.moderation.persistence.GuildWarnPointsSettingsRepository
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.Member
import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.entities.User
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.interactions.modals.ModalMapping
import net.dv8tion.jda.api.requests.restaction.WebhookMessageEditAction
import net.dv8tion.jda.api.requests.restaction.interactions.ReplyCallbackAction
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness
import java.util.Optional
import java.util.function.Consumer

@ExtendWith(MockitoExtension::class)
@MockitoSettings(strictness = Strictness.LENIENT)
class WarnCommandTest {
    @Mock
    private lateinit var guildWarnPointsSettingsRepository: GuildWarnPointsSettingsRepository

    @Mock
    private lateinit var slashEvent: SlashCommandInteractionEvent

    @Mock
    private lateinit var modalEvent: ModalInteractionEvent

    @Mock
    private lateinit var guild: Guild

    @Mock
    private lateinit var member: Member

    @Mock
    private lateinit var moderatorUser: User

    @Mock
    private lateinit var replyAction: ReplyCallbackAction

    @Mock
    private lateinit var modalReason: ModalMapping

    @Mock
    private lateinit var jda: JDA

    @Mock
    private lateinit var hook: net.dv8tion.jda.api.interactions.InteractionHook

    @Mock
    private lateinit var editAction: WebhookMessageEditAction<Message>

    @Mock
    private lateinit var guildLogger: GuildLogger

    private lateinit var command: WarnCommand

    @BeforeEach
    fun setUp() {
        command = WarnCommand(guildWarnPointsSettingsRepository)
    }

    @Test
    fun `missing manage roles permission returns error`() {
        whenever(slashEvent.name).thenReturn("warn")
        whenever(slashEvent.guild).thenReturn(guild)
        whenever(slashEvent.member).thenReturn(member)
        whenever(member.hasPermission(Permission.MANAGE_ROLES)).thenReturn(false)
        whenever(slashEvent.reply(any<String>())).thenReturn(replyAction)
        whenever(replyAction.setEphemeral(true)).thenReturn(replyAction)

        command.onSlashCommandInteraction(slashEvent)

        verify(slashEvent).reply("You need manage roles permission to use this command.")
    }

    @Test
    fun `modal submit logs warning when target left and tells moderator to warn manually`() {
        whenever(modalEvent.modalId).thenReturn("warn_reason:99")
        whenever(modalEvent.guild).thenReturn(guild)
        whenever(modalEvent.member).thenReturn(member)
        whenever(member.hasPermission(Permission.MANAGE_ROLES)).thenReturn(true)
        whenever(member.user).thenReturn(moderatorUser)
        whenever(member.nickname).thenReturn(null)
        whenever(moderatorUser.name).thenReturn("ModeratorUser")
        whenever(guild.idLong).thenReturn(1L)
        whenever(guild.getMemberById(99L)).thenReturn(null)
        whenever(guildWarnPointsSettingsRepository.findById(1L)).thenReturn(Optional.empty())
        whenever(modalEvent.getValue("reason")).thenReturn(modalReason)
        whenever(modalReason.asString).thenReturn("Spamming")
        whenever(modalEvent.jda).thenReturn(jda)
        whenever(jda.registeredListeners).thenReturn(listOf(guildLogger))
        whenever(modalEvent.deferReply(true)).thenReturn(replyAction)
        whenever(hook.editOriginal(any<String>())).thenReturn(editAction)
        doAnswer {
            val consumer = it.arguments[0] as Consumer<net.dv8tion.jda.api.interactions.InteractionHook>
            consumer.accept(hook)
            null
        }.whenever(replyAction).queue(any<Consumer<net.dv8tion.jda.api.interactions.InteractionHook>>())

        command.onModalInteraction(modalEvent)

        verify(guildLogger).log(any(), isNull(), eq(guild), isNull(), eq(GuildLogger.LogTypeAction.MODERATOR), isNull())
        verify(hook).editOriginal(
            "Warning logged for <@99>, but the user left the server before the warning could be delivered. Warn them manually if they rejoin."
        )
    }
}
