package be.duncanc.discordmodbot.moderation

import be.duncanc.discordmodbot.logging.GuildLogger
import be.duncanc.discordmodbot.moderation.persistence.GuildWarnPoint
import be.duncanc.discordmodbot.moderation.persistence.GuildWarnPointsSettings
import be.duncanc.discordmodbot.moderation.persistence.GuildWarnPointsSettingsRepository
import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.entities.Member
import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.entities.MessageEmbed
import net.dv8tion.jda.api.entities.User
import net.dv8tion.jda.api.entities.channel.concrete.PrivateChannel
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.interactions.InteractionHook
import net.dv8tion.jda.api.interactions.commands.OptionMapping
import net.dv8tion.jda.api.interactions.modals.ModalMapping
import net.dv8tion.jda.api.requests.restaction.AuditableRestAction
import net.dv8tion.jda.api.requests.restaction.CacheRestAction
import net.dv8tion.jda.api.requests.restaction.MessageCreateAction
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
import org.mockito.kotlin.any
import org.mockito.kotlin.argThat
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness
import java.time.OffsetDateTime
import java.util.Optional
import java.util.function.Consumer

@ExtendWith(MockitoExtension::class)
@MockitoSettings(strictness = Strictness.LENIENT)
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
    private lateinit var slashEvent: SlashCommandInteractionEvent

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
    private lateinit var targetUser: User

    @Mock
    private lateinit var jda: JDA

    @Mock
    private lateinit var hook: InteractionHook

    @Mock
    private lateinit var followupAction: WebhookMessageCreateAction<Message>

    @Mock
    private lateinit var editAction: WebhookMessageEditAction<Message>

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
    private lateinit var guildLogger: GuildLogger

    @Mock
    private lateinit var openPrivateChannelAction: CacheRestAction<PrivateChannel>

    @Mock
    private lateinit var privateChannel: PrivateChannel

    @Mock
    private lateinit var messageCreateAction: MessageCreateAction

    @Mock
    private lateinit var muteRole: net.dv8tion.jda.api.entities.Role

    @Mock
    private lateinit var addRoleAction: AuditableRestAction<Void>

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
    fun `slash command requires kick members when kick punishment is selected`() {
        whenever(slashEvent.name).thenReturn("addwarnpoints")
        whenever(slashEvent.member).thenReturn(member)
        whenever(member.hasPermission(Permission.MANAGE_ROLES)).thenReturn(true)
        whenever(member.hasPermission(Permission.KICK_MEMBERS)).thenReturn(false)
        whenever(slashEvent.getOption("user")).thenReturn(userOption)
        whenever(userOption.asMember).thenReturn(targetMember)
        whenever(member.canInteract(targetMember)).thenReturn(true)
        whenever(slashEvent.getOption("points")).thenReturn(pointsOption)
        whenever(pointsOption.asInt).thenReturn(2)
        whenever(slashEvent.getOption("days")).thenReturn(daysOption)
        whenever(daysOption.asInt).thenReturn(3)
        whenever(slashEvent.getOption("action")).thenReturn(actionOption)
        whenever(actionOption.asInt).thenReturn(2)
        whenever(slashEvent.reply(any<String>())).thenReturn(replyAction)
        whenever(replyAction.setEphemeral(true)).thenReturn(replyAction)

        command.onSlashCommandInteraction(slashEvent)

        verify(slashEvent).reply("You need kick members permission to apply the kick punishment.")
        verify(slashEvent, never()).replyModal(any())
    }

    @Test
    fun `mute slash command opens a combined modal`() {
        stubSlashCommand(action = 1)
        whenever(slashEvent.replyModal(any())).thenReturn(modalAction)

        command.onSlashCommandInteraction(slashEvent)

        verify(slashEvent).replyModal(argThat {
            id == "addwarnpoints_reason:99:2:3:1" && components.size == 2
        })
    }

    @Test
    fun `kick slash command opens a reason only modal`() {
        stubSlashCommand(action = 2)
        whenever(member.hasPermission(Permission.KICK_MEMBERS)).thenReturn(true)
        whenever(slashEvent.replyModal(any())).thenReturn(modalAction)

        command.onSlashCommandInteraction(slashEvent)

        verify(slashEvent).replyModal(argThat {
            id == "addwarnpoints_reason:99:2:3:2" && components.size == 1
        })
    }

    @Test
    fun `mute modal rejects invalid unmute days`() {
        stubModalContext(modalId = "addwarnpoints_reason:99:2:3:1")
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
        val embedCaptor = argumentCaptor<MessageEmbed>()

        stubSuccessfulMuteModalFlow(unmuteDays = null)

        command.onModalInteraction(modalEvent)

        verify(unmutePlanningService, never()).planUnmute(any(), any(), any(), any())
        verify(privateChannel).sendMessageEmbeds(embedCaptor.capture())
        kotlin.test.assertEquals(
            "Mute",
            embedCaptor.firstValue.fields.single { it.name == "Punishment" }.value
        )
    }

    @Test
    fun `mute modal with unmute days schedules unmute and uses duration punishment`() {
        val embedCaptor = argumentCaptor<MessageEmbed>()
        val logCaptor = argumentCaptor<EmbedBuilder>()
        val plannedUnmute = OffsetDateTime.now().plusDays(3)

        stubSuccessfulMuteModalFlow(unmuteDays = 3)
        whenever(unmutePlanningService.planUnmute(guild, 99L, member, 3)).thenReturn(plannedUnmute)

        command.onModalInteraction(modalEvent)

        verify(unmutePlanningService).planUnmute(guild, 99L, member, 3)
        verify(privateChannel).sendMessageEmbeds(embedCaptor.capture())
        verify(guildLogger).log(
            logCaptor.capture(),
            eq(targetUser),
            eq(guild),
            isNull<List<MessageEmbed>>(),
            eq(GuildLogger.LogTypeAction.MODERATOR),
            isNull()
        )
        kotlin.test.assertEquals(
            "3 days mute",
            embedCaptor.firstValue.fields.single { it.name == "Punishment" }.value
        )
        kotlin.test.assertEquals(
            "3 days mute",
            logCaptor.firstValue.build().fields.single { it.name == "Punishment" }.value
        )
    }

    @Test
    fun `mute modal without configured mute role still logs and attempts DM`() {
        val dmCaptor = argumentCaptor<MessageEmbed>()
        val logCaptor = argumentCaptor<EmbedBuilder>()
        val resultCaptor = argumentCaptor<String>()

        stubSuccessfulMuteModalFlow(unmuteDays = 3, muteRoleConfigured = false)

        command.onModalInteraction(modalEvent)

        verify(unmutePlanningService, never()).planUnmute(any(), any(), any(), any())
        verify(guildLogger).log(
            logCaptor.capture(),
            eq(targetUser),
            eq(guild),
            isNull<List<MessageEmbed>>(),
            eq(GuildLogger.LogTypeAction.MODERATOR),
            isNull()
        )
        verify(privateChannel).sendMessageEmbeds(dmCaptor.capture())
        verify(hook).sendMessage(resultCaptor.capture())
        kotlin.test.assertEquals(
            null,
            dmCaptor.firstValue.fields.singleOrNull { it.name == "Punishment" }
        )
        kotlin.test.assertEquals(
            null,
            logCaptor.firstValue.build().fields.singleOrNull { it.name == "Punishment" }
        )
        kotlin.test.assertEquals(true, resultCaptor.firstValue.contains("Mute role is not configured."))
    }

    @Test
    fun `mute modal when role assignment fails still logs and attempts DM`() {
        val dmCaptor = argumentCaptor<MessageEmbed>()
        val logCaptor = argumentCaptor<EmbedBuilder>()
        val resultCaptor = argumentCaptor<String>()

        stubSuccessfulMuteModalFlow(unmuteDays = 3, muteRoleAssignmentSucceeds = false)

        command.onModalInteraction(modalEvent)

        verify(unmutePlanningService, never()).planUnmute(any(), any(), any(), any())
        verify(guildLogger).log(
            logCaptor.capture(),
            eq(targetUser),
            eq(guild),
            isNull<List<MessageEmbed>>(),
            eq(GuildLogger.LogTypeAction.MODERATOR),
            isNull()
        )
        verify(privateChannel).sendMessageEmbeds(dmCaptor.capture())
        verify(hook).sendMessage(resultCaptor.capture())
        kotlin.test.assertEquals(
            null,
            dmCaptor.firstValue.fields.singleOrNull { it.name == "Punishment" }
        )
        kotlin.test.assertEquals(
            null,
            logCaptor.firstValue.build().fields.singleOrNull { it.name == "Punishment" }
        )
        kotlin.test.assertEquals(true, resultCaptor.firstValue.contains("Unable to add mute role to user."))
    }

    @Test
    fun `mute modal with failed unmute scheduling falls back to plain mute`() {
        val dmCaptor = argumentCaptor<MessageEmbed>()
        val logCaptor = argumentCaptor<EmbedBuilder>()
        val resultCaptor = argumentCaptor<String>()

        stubSuccessfulMuteModalFlow(unmuteDays = 366)
        whenever(unmutePlanningService.planUnmute(guild, 99L, member, 366))
            .thenThrow(IllegalArgumentException("Unmute cannot be scheduled beyond 365 days."))

        command.onModalInteraction(modalEvent)

        verify(unmutePlanningService).planUnmute(guild, 99L, member, 366)
        verify(guildLogger).log(
            logCaptor.capture(),
            eq(targetUser),
            eq(guild),
            isNull<List<MessageEmbed>>(),
            eq(GuildLogger.LogTypeAction.MODERATOR),
            isNull()
        )
        verify(privateChannel).sendMessageEmbeds(dmCaptor.capture())
        verify(hook).sendMessage(resultCaptor.capture())
        kotlin.test.assertEquals(
            "Mute",
            dmCaptor.firstValue.fields.single { it.name == "Punishment" }.value
        )
        kotlin.test.assertEquals(
            "Mute",
            logCaptor.firstValue.build().fields.single { it.name == "Punishment" }.value
        )
        kotlin.test.assertEquals(
            true,
            resultCaptor.firstValue.contains("Unmute cannot be scheduled beyond 365 days.")
        )
    }

    @Test
    fun `mute modal completes deferred reply when unexpected unmute planning fails`() {
        val resultCaptor = argumentCaptor<String>()

        stubSuccessfulMuteModalFlow(unmuteDays = 3)
        whenever(hook.sendMessage(any<String>())).thenReturn(followupAction)
        whenever(unmutePlanningService.planUnmute(guild, 99L, member, 3))
            .thenThrow(RuntimeException("Scheduler unavailable"))

        command.onModalInteraction(modalEvent)

        verify(unmutePlanningService).planUnmute(guild, 99L, member, 3)
        verify(hook).sendMessage(resultCaptor.capture())
        kotlin.test.assertEquals("Error: Scheduler unavailable", resultCaptor.firstValue)
    }

    @Test
    fun `mute modal completes deferred reply when role assignment failure handling throws`() {
        val resultCaptor = argumentCaptor<String>()

        stubSuccessfulMuteModalFlow(unmuteDays = 3, muteRoleAssignmentSucceeds = false)
        whenever(hook.sendMessage(any<String>())).thenReturn(followupAction)
        whenever(jda.registeredListeners).thenThrow(RuntimeException("Logger unavailable"))

        command.onModalInteraction(modalEvent)

        verify(hook).sendMessage(resultCaptor.capture())
        kotlin.test.assertEquals("Error: Logger unavailable", resultCaptor.firstValue)
    }

    private fun stubSlashCommand(action: Int) {
        whenever(slashEvent.name).thenReturn("addwarnpoints")
        whenever(slashEvent.member).thenReturn(member)
        whenever(member.hasPermission(Permission.MANAGE_ROLES)).thenReturn(true)
        whenever(slashEvent.getOption("user")).thenReturn(userOption)
        whenever(userOption.asMember).thenReturn(targetMember)
        whenever(targetMember.idLong).thenReturn(99L)
        whenever(member.canInteract(targetMember)).thenReturn(true)
        whenever(slashEvent.getOption("points")).thenReturn(pointsOption)
        whenever(pointsOption.asInt).thenReturn(2)
        whenever(slashEvent.getOption("days")).thenReturn(daysOption)
        whenever(daysOption.asInt).thenReturn(3)
        whenever(slashEvent.getOption("action")).thenReturn(actionOption)
        whenever(actionOption.asInt).thenReturn(action)
    }

    private fun stubModalContext(modalId: String) {
        whenever(modalEvent.modalId).thenReturn(modalId)
        whenever(modalEvent.guild).thenReturn(guild)
        whenever(modalEvent.member).thenReturn(member)
        whenever(member.hasPermission(Permission.MANAGE_ROLES)).thenReturn(true)
        whenever(guild.getMemberById("99")).thenReturn(targetMember)
        whenever(member.canInteract(targetMember)).thenReturn(true)
    }

    @Suppress("UNCHECKED_CAST")
    private fun stubSuccessfulMuteModalFlow(
        unmuteDays: Int?,
        muteRoleConfigured: Boolean = true,
        muteRoleAssignmentSucceeds: Boolean = true
    ) {
        val settings = GuildWarnPointsSettings(
            guildId = 1L,
            maxPointsPerReason = 10,
            announcePointsSummaryLimit = 99,
            announceChannelId = 1L,
            overrideWarnCommand = false
        )
        val warnPoint = GuildWarnPoint(
            userId = 99L,
            guildId = 1L,
            points = 2,
            creatorId = 12L,
            reason = "Spamming",
            expireDate = OffsetDateTime.now().plusDays(3)
        )

        stubModalContext(modalId = "addwarnpoints_reason:99:2:3:1")
        whenever(modalEvent.jda).thenReturn(jda)
        whenever(member.guild).thenReturn(guild)
        whenever(member.idLong).thenReturn(12L)
        whenever(member.user).thenReturn(moderatorUser)
        whenever(moderatorUser.name).thenReturn("ModeratorUser")
        whenever(moderatorUser.effectiveAvatarUrl).thenReturn("https://example.com/mod.png")
        whenever(guild.idLong).thenReturn(1L)
        whenever(guild.name).thenReturn("Guild")
        whenever(targetMember.idLong).thenReturn(99L)
        whenever(targetMember.guild).thenReturn(guild)
        whenever(targetMember.user).thenReturn(targetUser)
        whenever(targetMember.nickname).thenReturn(null)
        whenever(targetUser.name).thenReturn("TargetUser")
        whenever(jda.registeredListeners).thenReturn(listOf(guildLogger))
        whenever(jda.getTextChannelById(1L)).thenReturn(mockTextChannel())
        whenever(guildWarnPointsSettingsRepository.findById(1L)).thenReturn(Optional.of(settings))
        whenever(modalEvent.getValue("reason")).thenReturn(reasonValue)
        whenever(reasonValue.asString).thenReturn("Spamming")
        whenever(modalEvent.getValue("unmute_days")).thenReturn(unmuteDaysValue)
        whenever(unmuteDaysValue.asString).thenReturn(unmuteDays?.toString().orEmpty())
        whenever(modalEvent.deferReply(true)).thenReturn(replyAction)
        whenever(guildWarnPointsService.addWarnPoint(eq(99L), eq(1L), eq(2), eq(12L), eq("Spamming"), any()))
            .thenReturn(warnPoint)
        whenever(guildWarnPointsService.getActivePointsCount(1L, 99L)).thenReturn(1)
        if (muteRoleConfigured) {
            whenever(muteRoleCommandAndEventsListener.getMuteRole(guild)).thenReturn(muteRole)
        } else {
            whenever(muteRoleCommandAndEventsListener.getMuteRole(guild))
                .thenThrow(IllegalStateException("Mute role not configured"))
        }
        whenever(guild.addRoleToMember(targetMember, muteRole)).thenReturn(addRoleAction)
        whenever(addRoleAction.reason("Spamming")).thenReturn(addRoleAction)
        doAnswer {
            val consumer = it.arguments[0] as Consumer<InteractionHook>
            consumer.accept(hook)
            null
        }.whenever(replyAction).queue(any<Consumer<InteractionHook>>())
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
        whenever(targetUser.openPrivateChannel()).thenReturn(openPrivateChannelAction)
        doAnswer {
            val success = it.arguments[0] as Consumer<PrivateChannel>
            success.accept(privateChannel)
            null
        }.whenever(openPrivateChannelAction).queue(any<Consumer<PrivateChannel>>(), any<Consumer<Throwable>>())
        whenever(privateChannel.sendMessageEmbeds(any<MessageEmbed>())).thenReturn(messageCreateAction)
        doAnswer {
            val failure = it.arguments[1] as Consumer<Throwable>
            failure.accept(IllegalStateException("DMs disabled"))
            null
        }.whenever(messageCreateAction).queue(any<Consumer<Message>>(), any<Consumer<Throwable>>())
        whenever(hook.sendMessage(any<String>())).thenReturn(followupAction)
        whenever(followupAction.setEphemeral(true)).thenReturn(followupAction)
    }

    private fun mockTextChannel(): net.dv8tion.jda.api.entities.channel.concrete.TextChannel {
        return org.mockito.kotlin.mock()
    }
}
