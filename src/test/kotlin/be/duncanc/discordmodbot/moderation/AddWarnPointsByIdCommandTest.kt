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
import net.dv8tion.jda.api.requests.restaction.WebhookMessageCreateAction
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
    private lateinit var unmutePlanningService: UnmutePlanningService

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
    private lateinit var followupAction: WebhookMessageCreateAction<net.dv8tion.jda.api.entities.Message>

    @Mock
    private lateinit var userOption: OptionMapping

    @Mock
    private lateinit var pointsOption: OptionMapping

    @Mock
    private lateinit var daysOption: OptionMapping

    @Mock
    private lateinit var actionOption: OptionMapping

    @Mock
    private lateinit var reasonValue: ModalMapping

    @Mock
    private lateinit var unmuteDaysValue: ModalMapping

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
            unmutePlanningService,
            guildLogger
        )
    }

    @Test
    fun `opens a reason modal`() {
        stubSlashContext(targetMember = null)
        whenever(slashEvent.replyModal(any())).thenReturn(modalAction)

        command.onSlashCommandInteraction(slashEvent)

        verify(slashEvent).replyModal(argThat { id == "addwarnpointsbyid_reason:99:2:3:0" })
    }

    @Test
    fun `mute slash command opens a combined modal`() {
        stubSlashContext(targetMember = null, action = 1)
        whenever(slashEvent.replyModal(any())).thenReturn(modalAction)

        command.onSlashCommandInteraction(slashEvent)

        verify(slashEvent).replyModal(argThat {
            id == "addwarnpointsbyid_reason:99:2:3:1" && components.size == 2
        })
    }

    @Test
    fun `modal submission stores warn points for a departed user`() {
        val warnPoint = warnPoint()
        val settings = settings()

        whenever(modalEvent.modalId).thenReturn("addwarnpointsbyid_reason:99:2:3:0")
        whenever(modalEvent.member).thenReturn(member)
        whenever(modalEvent.guild).thenReturn(guild)
        whenever(modalEvent.jda).thenReturn(jda)
        whenever(member.hasPermission(Permission.MANAGE_ROLES)).thenReturn(true)
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
        whenever(interactionHook.sendMessage(any<String>())).thenReturn(followupAction)
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
        verify(interactionHook).sendMessage(
            """Added warn points to <@99>.
The user was not warned by DM, please do so manually when they rejoin."""
        )
    }

    @Test
    fun `modal submission rejects points above the configured cap`() {
        val settings = settings()

        whenever(modalEvent.modalId).thenReturn("addwarnpointsbyid_reason:99:11:3:0")
        whenever(modalEvent.member).thenReturn(member)
        whenever(modalEvent.guild).thenReturn(guild)
        whenever(member.hasPermission(Permission.MANAGE_ROLES)).thenReturn(true)
        whenever(member.idLong).thenReturn(12L)
        whenever(guild.idLong).thenReturn(1L)
        whenever(guild.getMemberById(99L)).thenReturn(null)
        whenever(modalEvent.getValue("reason")).thenReturn(reasonValue)
        whenever(reasonValue.asString).thenReturn("Spamming")
        whenever(guildWarnPointsSettingsRepository.findById(1L)).thenReturn(Optional.of(settings))
        whenever(modalEvent.reply(any<String>())).thenReturn(replyAction)
        whenever(replyAction.setEphemeral(true)).thenReturn(replyAction)

        command.onModalInteraction(modalEvent)

        verify(modalEvent).reply("The maximum points per reason is 10.")
        verify(modalEvent, never()).deferReply(true)
        verify(guildWarnPointsService, never()).addWarnPoint(any(), any(), any(), any(), any(), any())
    }

    @Test
    fun `mute modal rejects invalid unmute days`() {
        whenever(modalEvent.modalId).thenReturn("addwarnpointsbyid_reason:99:2:3:1")
        whenever(modalEvent.member).thenReturn(member)
        whenever(modalEvent.guild).thenReturn(guild)
        whenever(member.hasPermission(Permission.MANAGE_ROLES)).thenReturn(true)
        whenever(guild.getMemberById(99L)).thenReturn(null)
        whenever(modalEvent.getValue("reason")).thenReturn(reasonValue)
        whenever(reasonValue.asString).thenReturn("Spamming")
        whenever(modalEvent.getValue("unmute_days")).thenReturn(unmuteDaysValue)
        whenever(unmuteDaysValue.asString).thenReturn("abc")
        whenever(modalEvent.reply(any<String>())).thenReturn(replyAction)
        whenever(replyAction.setEphemeral(true)).thenReturn(replyAction)

        command.onModalInteraction(modalEvent)

        verify(modalEvent).reply("Please provide a valid number of days.")
        verify(modalEvent, never()).deferReply(true)
    }

    @Test
    fun `mute modal without unmute days keeps punishment as mute`() {
        val embedCaptor = argumentCaptor<net.dv8tion.jda.api.EmbedBuilder>()
        val resultCaptor = argumentCaptor<String>()

        stubSuccessfulMuteModalFlow(unmuteDays = null)

        command.onModalInteraction(modalEvent)

        verify(unmutePlanningService, never()).planUnmute(any(), any(), any(), any())
        verify(muteService).muteUserById(1L, 99L)
        verify(guildLogger).log(
            embedCaptor.capture(),
            isNull(),
            eq(guild),
            isNull(),
            eq(GuildLogger.LogTypeAction.MODERATOR),
            isNull()
        )
        verify(interactionHook).sendMessage(resultCaptor.capture())
        kotlin.test.assertEquals(
            "Mute",
            embedCaptor.firstValue.build().fields.single { it.name == "Punishment" }.value
        )
        kotlin.test.assertEquals(
            """Added warn points to <@99>.
The mute will be applied when they rejoin.
The user was not warned by DM, please do so manually when they rejoin.""",
            resultCaptor.firstValue
        )
    }

    @Test
    fun `mute modal with unmute days schedules unmute and uses duration punishment`() {
        val embedCaptor = argumentCaptor<net.dv8tion.jda.api.EmbedBuilder>()
        val resultCaptor = argumentCaptor<String>()
        val plannedUnmute = OffsetDateTime.now().plusDays(3)

        stubSuccessfulMuteModalFlow(unmuteDays = 3)
        whenever(unmutePlanningService.planUnmute(guild, 99L, member, 3)).thenReturn(plannedUnmute)

        command.onModalInteraction(modalEvent)

        verify(unmutePlanningService).planUnmute(guild, 99L, member, 3)
        verify(guildLogger).log(
            embedCaptor.capture(),
            isNull(),
            eq(guild),
            isNull(),
            eq(GuildLogger.LogTypeAction.MODERATOR),
            isNull()
        )
        verify(interactionHook).sendMessage(resultCaptor.capture())
        kotlin.test.assertEquals(
            "3 days mute",
            embedCaptor.firstValue.build().fields.single { it.name == "Punishment" }.value
        )
        kotlin.test.assertEquals(true, resultCaptor.firstValue.contains("Unmute planned for"))
    }

    @Test
    fun `mute modal with failed unmute scheduling falls back to plain mute`() {
        val embedCaptor = argumentCaptor<net.dv8tion.jda.api.EmbedBuilder>()
        val resultCaptor = argumentCaptor<String>()

        stubSuccessfulMuteModalFlow(unmuteDays = 366)
        whenever(unmutePlanningService.planUnmute(guild, 99L, member, 366))
            .thenThrow(IllegalArgumentException("A mute can't take longer than 1 year"))

        command.onModalInteraction(modalEvent)

        verify(unmutePlanningService).planUnmute(guild, 99L, member, 366)
        verify(guildLogger).log(
            embedCaptor.capture(),
            isNull(),
            eq(guild),
            isNull(),
            eq(GuildLogger.LogTypeAction.MODERATOR),
            isNull()
        )
        verify(interactionHook).sendMessage(resultCaptor.capture())
        kotlin.test.assertEquals(
            "Mute",
            embedCaptor.firstValue.build().fields.single { it.name == "Punishment" }.value
        )
        kotlin.test.assertEquals(true, resultCaptor.firstValue.contains("A mute can't take longer than 1 year"))
    }

    @Test
    fun `mute modal for present member schedules unmute only after role assignment succeeds`() {
        val embedCaptor = argumentCaptor<net.dv8tion.jda.api.EmbedBuilder>()
        val resultCaptor = argumentCaptor<String>()
        val plannedUnmute = OffsetDateTime.now().plusDays(3)

        stubSuccessfulMuteModalFlow(unmuteDays = 3, targetMemberPresent = true)
        whenever(unmutePlanningService.planUnmute(guild, 99L, member, 3)).thenReturn(plannedUnmute)

        command.onModalInteraction(modalEvent)

        verify(muteService).muteUserById(1L, 99L)
        verify(unmutePlanningService).planUnmute(guild, 99L, member, 3)
        verify(guildLogger).log(
            embedCaptor.capture(),
            eq(targetUser),
            eq(guild),
            isNull(),
            eq(GuildLogger.LogTypeAction.MODERATOR),
            isNull()
        )
        verify(interactionHook).sendMessage(resultCaptor.capture())
        kotlin.test.assertEquals(
            "3 days mute",
            embedCaptor.firstValue.build().fields.single { it.name == "Punishment" }.value
        )
        kotlin.test.assertEquals(true, resultCaptor.firstValue.contains("Unmute planned for"))
    }

    @Test
    fun `mute modal for present member does not persist mute or schedule unmute when role assignment fails`() {
        val embedCaptor = argumentCaptor<net.dv8tion.jda.api.EmbedBuilder>()
        val resultCaptor = argumentCaptor<String>()

        stubSuccessfulMuteModalFlow(unmuteDays = 3, targetMemberPresent = true, muteRoleAssignmentSucceeds = false)

        command.onModalInteraction(modalEvent)

        verify(muteService, never()).muteUserById(any(), any())
        verify(unmutePlanningService, never()).planUnmute(any(), any(), any(), any())
        verify(guildLogger).log(
            embedCaptor.capture(),
            eq(targetUser),
            eq(guild),
            isNull(),
            eq(GuildLogger.LogTypeAction.MODERATOR),
            isNull()
        )
        verify(interactionHook).sendMessage(resultCaptor.capture())
        kotlin.test.assertEquals(
            null,
            embedCaptor.firstValue.build().fields.singleOrNull { it.name == "Punishment" }
        )
        kotlin.test.assertEquals(true, resultCaptor.firstValue.contains("Unable to add mute role to user."))
        kotlin.test.assertEquals(false, resultCaptor.firstValue.contains("Unmute planned for"))
    }

    @Test
    fun `mute modal for present member completes deferred reply when mute persistence fails after role assignment`() {
        val embedCaptor = argumentCaptor<net.dv8tion.jda.api.EmbedBuilder>()
        val resultCaptor = argumentCaptor<String>()

        stubSuccessfulMuteModalFlow(unmuteDays = 3, targetMemberPresent = true)
        whenever(muteService.muteUserById(1L, 99L)).thenThrow(IllegalStateException("Failed to persist mute state"))

        command.onModalInteraction(modalEvent)

        verify(muteService).muteUserById(1L, 99L)
        verify(unmutePlanningService, never()).planUnmute(any(), any(), any(), any())
        verify(guildLogger).log(
            embedCaptor.capture(),
            eq(targetUser),
            eq(guild),
            isNull(),
            eq(GuildLogger.LogTypeAction.MODERATOR),
            isNull()
        )
        verify(interactionHook).sendMessage(resultCaptor.capture())
        kotlin.test.assertEquals(
            "Mute",
            embedCaptor.firstValue.build().fields.single { it.name == "Punishment" }.value
        )
        kotlin.test.assertEquals(true, resultCaptor.firstValue.contains("Error: Failed to persist mute state"))
        kotlin.test.assertEquals(true, resultCaptor.firstValue.contains("Please plan the unmute manually."))
        kotlin.test.assertEquals(false, resultCaptor.firstValue.contains("Unmute planned for"))
    }

    @Test
    fun `mute modal for present member completes deferred reply when unexpected unmute planning fails`() {
        val embedCaptor = argumentCaptor<net.dv8tion.jda.api.EmbedBuilder>()
        val resultCaptor = argumentCaptor<String>()

        stubSuccessfulMuteModalFlow(unmuteDays = 3, targetMemberPresent = true)
        whenever(unmutePlanningService.planUnmute(guild, 99L, member, 3))
            .thenThrow(RuntimeException("Scheduler unavailable"))

        command.onModalInteraction(modalEvent)

        verify(muteService).muteUserById(1L, 99L)
        verify(unmutePlanningService).planUnmute(guild, 99L, member, 3)
        verify(guildLogger).log(
            embedCaptor.capture(),
            eq(targetUser),
            eq(guild),
            isNull(),
            eq(GuildLogger.LogTypeAction.MODERATOR),
            isNull()
        )
        verify(interactionHook).sendMessage(resultCaptor.capture())
        kotlin.test.assertEquals(
            "Mute",
            embedCaptor.firstValue.build().fields.single { it.name == "Punishment" }.value
        )
        kotlin.test.assertEquals(true, resultCaptor.firstValue.contains("Error: Scheduler unavailable"))
        kotlin.test.assertEquals(true, resultCaptor.firstValue.contains("Please plan the unmute manually."))
        kotlin.test.assertEquals(false, resultCaptor.firstValue.contains("Unmute planned for"))
    }

    private fun stubSlashContext(
        targetMember: Member?,
        action: Int = 0
    ) {
        whenever(slashEvent.name).thenReturn("addwarnpointsbyid")
        whenever(slashEvent.member).thenReturn(member)
        whenever(member.hasPermission(Permission.MANAGE_ROLES)).thenReturn(true)
        whenever(slashEvent.getOption("user")).thenReturn(userOption)
        whenever(slashEvent.getOption("points")).thenReturn(pointsOption)
        whenever(slashEvent.getOption("days")).thenReturn(daysOption)
        whenever(slashEvent.getOption("action")).thenReturn(actionOption)
        whenever(userOption.asLong).thenReturn(99L)
        whenever(userOption.asMember).thenReturn(targetMember)
        whenever(pointsOption.asInt).thenReturn(2)
        whenever(daysOption.asInt).thenReturn(3)
        whenever(actionOption.asInt).thenReturn(action)
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
    private fun stubSuccessfulMuteModalFlow(
        unmuteDays: Int?,
        targetMemberPresent: Boolean = false,
        muteRoleAssignmentSucceeds: Boolean = true
    ) {
        val warnPoint = warnPoint()
        val settings = settings()

        whenever(modalEvent.modalId).thenReturn("addwarnpointsbyid_reason:99:2:3:1")
        whenever(modalEvent.member).thenReturn(member)
        whenever(modalEvent.guild).thenReturn(guild)
        whenever(modalEvent.jda).thenReturn(jda)
        whenever(member.hasPermission(Permission.MANAGE_ROLES)).thenReturn(true)
        whenever(member.idLong).thenReturn(12L)
        whenever(member.nickname).thenReturn(null)
        whenever(member.user).thenReturn(moderatorUser)
        whenever(moderatorUser.name).thenReturn("ModeratorUser")
        whenever(guild.idLong).thenReturn(1L)
        whenever(guild.getMemberById(99L)).thenReturn(if (targetMemberPresent) targetMember else null)
        whenever(member.canInteract(targetMember)).thenReturn(true)
        whenever(modalEvent.getValue("reason")).thenReturn(reasonValue)
        whenever(reasonValue.asString).thenReturn("Spamming")
        whenever(modalEvent.getValue("unmute_days")).thenReturn(unmuteDaysValue)
        whenever(unmuteDaysValue.asString).thenReturn(unmuteDays?.toString().orEmpty())
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
        whenever(interactionHook.sendMessage(any<String>())).thenReturn(followupAction)
        whenever(muteRoleCommandAndEventsListener.getMuteRole(guild)).thenReturn(muteRole)
        whenever(targetMember.asMention).thenReturn("<@99>")
        whenever(targetMember.user).thenReturn(targetUser)
        whenever(guild.addRoleToMember(targetMember, muteRole)).thenReturn(addRoleAction)
        whenever(addRoleAction.reason("Spamming")).thenReturn(addRoleAction)
        if (muteRoleAssignmentSucceeds) {
            doAnswer {
                val success = it.arguments[0] as Consumer<Void?>
                success.accept(null)
                null
            }.whenever(addRoleAction).queue(any<Consumer<Void?>>(), any<Consumer<Throwable>>())
        } else {
            doAnswer {
                val failure = it.arguments[1] as Consumer<Throwable>
                failure.accept(IllegalStateException("Missing permissions"))
                null
            }.whenever(addRoleAction).queue(any<Consumer<Void?>>(), any<Consumer<Throwable>>())
        }
        doQueue(replyAction)
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
