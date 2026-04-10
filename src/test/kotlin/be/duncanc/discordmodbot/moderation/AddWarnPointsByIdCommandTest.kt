package be.duncanc.discordmodbot.moderation

import be.duncanc.discordmodbot.logging.GuildLogger
import be.duncanc.discordmodbot.moderation.persistence.GuildWarnPoint
import be.duncanc.discordmodbot.moderation.persistence.GuildWarnPointsSettings
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.Member
import net.dv8tion.jda.api.entities.Role
import net.dv8tion.jda.api.entities.User
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.interactions.InteractionHook
import net.dv8tion.jda.api.interactions.commands.OptionMapping
import net.dv8tion.jda.api.interactions.modals.ModalMapping
import net.dv8tion.jda.api.requests.restaction.AuditableRestAction
import net.dv8tion.jda.api.requests.restaction.WebhookMessageEditAction
import net.dv8tion.jda.api.requests.restaction.interactions.ModalCallbackAction
import net.dv8tion.jda.api.requests.restaction.interactions.ReplyCallbackAction
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.*
import org.mockito.quality.Strictness
import java.time.OffsetDateTime
import java.util.*
import java.util.function.Consumer

@ExtendWith(MockitoExtension::class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AddWarnPointsByIdCommandTest {
    @Mock
    private lateinit var guildWarnPointsService: GuildWarnPointsService

    @Mock
    private lateinit var guildWarnPointsSettingsRepository: be.duncanc.discordmodbot.moderation.persistence.GuildWarnPointsSettingsRepository

    @Mock
    private lateinit var muteRoleCommandAndEventsListener: MuteRoleCommandAndEventsListener

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
    private lateinit var targetMember: Member

    @Mock
    private lateinit var moderatorUser: User

    @Mock
    private lateinit var targetUser: User

    @Mock
    private lateinit var guild: Guild

    @Mock
    private lateinit var textChannel: TextChannel

    @Mock
    private lateinit var jda: JDA

    @Mock
    private lateinit var replyAction: ReplyCallbackAction

    @Mock
    private lateinit var modalAction: ModalCallbackAction

    @Mock
    private lateinit var interactionHook: InteractionHook

    @Mock
    private lateinit var editAction: WebhookMessageEditAction<net.dv8tion.jda.api.entities.Message>

    @Mock
    private lateinit var userOption: OptionMapping

    @Mock
    private lateinit var pointsOption: OptionMapping

    @Mock
    private lateinit var daysOption: OptionMapping

    @Mock
    private lateinit var actionOption: OptionMapping

    @Mock
    private lateinit var reasonOption: OptionMapping

    @Mock
    private lateinit var reasonValue: ModalMapping

    @Mock
    private lateinit var muteRole: Role

    @Mock
    private lateinit var addRoleAction: AuditableRestAction<Void>

    private lateinit var command: AddWarnPointsByIdCommand

    @BeforeEach
    fun setUp() {
        command = AddWarnPointsByIdCommand(
            guildWarnPointsService,
            guildWarnPointsSettingsRepository,
            muteRoleCommandAndEventsListener,
            muteService,
            guildLogger
        )
    }

    @Test
    fun `opens a reason modal when reason is omitted`() {
        stubSlashContext(reason = null, targetMember = null, configureSettings = false)
        whenever(slashEvent.replyModal(any())).thenReturn(modalAction)

        command.onSlashCommandInteraction(slashEvent)

        verify(slashEvent).replyModal(argThat { id == "addwarnpointsbyid_reason:99:2:3:0" })
    }

    @Test
    fun `stores warn points for a user who left the server`() {
        val warnPoint = warnPoint()
        val settings = settings()

        stubSlashContext(reason = "Spamming", targetMember = null, settings = settings)
        whenever(
            guildWarnPointsService.addWarnPoint(
                eq(99L),
                eq(1L),
                eq(2),
                eq(12L),
                eq("Spamming"),
                any<OffsetDateTime>()
            )
        ).thenReturn(warnPoint)
        whenever(guildWarnPointsService.getActivePointsCount(1L, 99L)).thenReturn(1)
        whenever(slashEvent.deferReply(true)).thenReturn(replyAction)
        whenever(interactionHook.editOriginal(any<String>())).thenReturn(editAction)
        doQueue(replyAction)

        command.onSlashCommandInteraction(slashEvent)

        verify(guildWarnPointsService).addWarnPoint(
            eq(99L),
            eq(1L),
            eq(2),
            eq(12L),
            eq("Spamming"),
            any<OffsetDateTime>()
        )
        verify(interactionHook).editOriginal(
            """Added warn points to <@99>.
The user was not warned by DM, please do so manually when they rejoin."""
        )
        verify(muteService, never()).muteUserById(1L, 99L)
    }

    @Test
    fun `mutes a present user by id`() {
        val warnPoint = warnPoint()
        val settings = settings()

        stubSlashContext(reason = "Spamming", targetMember = targetMember, settings = settings, action = 1)
        whenever(member.canInteract(targetMember)).thenReturn(true)
        whenever(targetMember.asMention).thenReturn("<@99>")
        whenever(targetMember.user).thenReturn(targetUser)
        whenever(muteRoleCommandAndEventsListener.getMuteRole(guild)).thenReturn(muteRole)
        whenever(guild.addRoleToMember(targetMember, muteRole)).thenReturn(addRoleAction)
        whenever(addRoleAction.reason("Spamming")).thenReturn(addRoleAction)
        whenever(
            guildWarnPointsService.addWarnPoint(
                eq(99L),
                eq(1L),
                eq(2),
                eq(12L),
                eq("Spamming"),
                any<OffsetDateTime>()
            )
        ).thenReturn(warnPoint)
        whenever(guildWarnPointsService.getActivePointsCount(1L, 99L)).thenReturn(1)
        whenever(slashEvent.deferReply(true)).thenReturn(replyAction)
        whenever(interactionHook.editOriginal(any<String>())).thenReturn(editAction)
        doQueue(replyAction)

        command.onSlashCommandInteraction(slashEvent)

        verify(muteService).muteUserById(1L, 99L)
        verify(addRoleAction).queue()
        verify(interactionHook).editOriginal(
            """Added warn points to <@99> and tried to apply the mute role.
The user was present but not warned by DM, please do so manually."""
        )
    }

    @Test
    fun `mutes a departed user by id without trying to add the role immediately`() {
        val warnPoint = warnPoint()
        val settings = settings()

        stubSlashContext(reason = "Spamming", targetMember = null, settings = settings, action = 1)
        whenever(muteRoleCommandAndEventsListener.getMuteRole(guild)).thenReturn(muteRole)
        whenever(
            guildWarnPointsService.addWarnPoint(
                eq(99L),
                eq(1L),
                eq(2),
                eq(12L),
                eq("Spamming"),
                any<OffsetDateTime>()
            )
        ).thenReturn(warnPoint)
        whenever(guildWarnPointsService.getActivePointsCount(1L, 99L)).thenReturn(1)
        whenever(slashEvent.deferReply(true)).thenReturn(replyAction)
        whenever(interactionHook.editOriginal(any<String>())).thenReturn(editAction)
        doQueue(replyAction)

        command.onSlashCommandInteraction(slashEvent)

        verify(muteService).muteUserById(1L, 99L)
        verify(guild, never()).addRoleToMember(targetMember, muteRole)
        verify(interactionHook).editOriginal(
            """Added warn points to <@99>.
The mute will be applied when they rejoin.
The user was not warned by DM, please do so manually when they rejoin."""
        )
    }

    @Test
    fun `modal submission stores warn points for a departed user`() {
        val warnPoint = warnPoint()
        val settings = settings()

        whenever(modalEvent.modalId).thenReturn("addwarnpointsbyid_reason:99:2:3:0")
        whenever(modalEvent.member).thenReturn(member)
        whenever(modalEvent.guild).thenReturn(guild)
        whenever(modalEvent.jda).thenReturn(jda)
        whenever(member.hasPermission(Permission.KICK_MEMBERS)).thenReturn(true)
        whenever(member.idLong).thenReturn(12L)
        whenever(member.user).thenReturn(moderatorUser)
        whenever(moderatorUser.name).thenReturn("ModeratorUser")
        whenever(guild.idLong).thenReturn(1L)
        whenever(guild.getMemberById(99L)).thenReturn(null)
        whenever(modalEvent.getValue("reason")).thenReturn(reasonValue)
        whenever(reasonValue.asString).thenReturn("Spamming")
        whenever(guildWarnPointsSettingsRepository.findById(1L)).thenReturn(Optional.of(settings))
        whenever(jda.getTextChannelById(1L)).thenReturn(textChannel)
        whenever(
            guildWarnPointsService.addWarnPoint(
                eq(99L),
                eq(1L),
                eq(2),
                eq(12L),
                eq("Spamming"),
                any<OffsetDateTime>()
            )
        ).thenReturn(warnPoint)
        whenever(guildWarnPointsService.getActivePointsCount(1L, 99L)).thenReturn(1)
        whenever(modalEvent.deferReply(true)).thenReturn(replyAction)
        whenever(interactionHook.editOriginal(any<String>())).thenReturn(editAction)
        doQueue(replyAction)

        command.onModalInteraction(modalEvent)

        verify(guildWarnPointsService).addWarnPoint(
            eq(99L),
            eq(1L),
            eq(2),
            eq(12L),
            eq("Spamming"),
            any<OffsetDateTime>()
        )
        verify(interactionHook).editOriginal(
            """Added warn points to <@99>.
The user was not warned by DM, please do so manually when they rejoin."""
        )
    }

    private fun stubSlashContext(
        reason: String?,
        targetMember: Member?,
        settings: GuildWarnPointsSettings = settings(),
        action: Int = 0,
        configureSettings: Boolean = true
    ) {
        whenever(slashEvent.name).thenReturn("addwarnpointsbyid")
        whenever(slashEvent.member).thenReturn(member)
        whenever(member.hasPermission(Permission.KICK_MEMBERS)).thenReturn(true)
        whenever(slashEvent.getOption("user")).thenReturn(userOption)
        whenever(slashEvent.getOption("points")).thenReturn(pointsOption)
        whenever(slashEvent.getOption("days")).thenReturn(daysOption)
        whenever(slashEvent.getOption("action")).thenReturn(actionOption)
        whenever(slashEvent.getOption("reason")).thenReturn(reasonOption)
        whenever(userOption.asLong).thenReturn(99L)
        whenever(userOption.asMember).thenReturn(targetMember)
        whenever(pointsOption.asInt).thenReturn(2)
        whenever(daysOption.asInt).thenReturn(3)
        whenever(actionOption.asInt).thenReturn(action)
        whenever(reasonOption.asString).thenReturn(reason)
        if (reason != null) {
            whenever(slashEvent.guild).thenReturn(guild)
            whenever(member.guild).thenReturn(guild)
            whenever(member.idLong).thenReturn(12L)
            whenever(guild.idLong).thenReturn(1L)
        }
        if (configureSettings) {
            whenever(slashEvent.jda).thenReturn(jda)
            whenever(guildWarnPointsSettingsRepository.findById(1L)).thenReturn(Optional.of(settings))
            whenever(jda.getTextChannelById(1L)).thenReturn(textChannel)
        }
        if (reason != null) {
            whenever(moderatorUser.name).thenReturn("ModeratorUser")
            whenever(member.user).thenReturn(moderatorUser)
        }
    }

    private fun settings(): GuildWarnPointsSettings {
        return GuildWarnPointsSettings(
            guildId = 1L,
            maxPointsPerReason = 10,
            announcePointsSummaryLimit = 99,
            announceChannelId = 1L,
            overrideWarnCommand = false
        )
    }

    private fun warnPoint(): GuildWarnPoint {
        return GuildWarnPoint(
            userId = 99L,
            guildId = 1L,
            points = 2,
            creatorId = 12L,
            reason = "Spamming",
            expireDate = OffsetDateTime.now().plusDays(2)
        )
    }

    @Suppress("UNCHECKED_CAST")
    private fun doQueue(reply: ReplyCallbackAction) {
        doAnswer {
            val consumer = it.arguments[0] as Consumer<InteractionHook>
            consumer.accept(interactionHook)
            null
        }.whenever(reply).queue(any<Consumer<InteractionHook>>())
    }
}
