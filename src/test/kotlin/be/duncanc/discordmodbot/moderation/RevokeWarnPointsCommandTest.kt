package be.duncanc.discordmodbot.moderation

import be.duncanc.discordmodbot.moderation.persistence.GuildWarnPoint
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.components.actionrow.ActionRow
import net.dv8tion.jda.api.components.selections.StringSelectMenu
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.Member
import net.dv8tion.jda.api.entities.User
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent
import net.dv8tion.jda.api.interactions.commands.OptionMapping
import net.dv8tion.jda.api.interactions.commands.OptionType
import net.dv8tion.jda.api.interactions.modals.ModalMapping
import net.dv8tion.jda.api.requests.restaction.interactions.MessageEditCallbackAction
import net.dv8tion.jda.api.requests.restaction.interactions.ModalCallbackAction
import net.dv8tion.jda.api.requests.restaction.interactions.ReplyCallbackAction
import net.dv8tion.jda.api.utils.data.DataObject
import net.dv8tion.jda.api.utils.messages.MessageCreateData
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.any
import org.mockito.kotlin.argThat
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness
import java.time.OffsetDateTime
import java.util.UUID

@ExtendWith(MockitoExtension::class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RevokeWarnPointsCommandTest {
    @Mock
    private lateinit var guildWarnPointsService: GuildWarnPointsService

    @Mock
    private lateinit var slashEvent: SlashCommandInteractionEvent

    @Mock
    private lateinit var selectEvent: StringSelectInteractionEvent

    @Mock
    private lateinit var buttonEvent: ButtonInteractionEvent

    @Mock
    private lateinit var modalEvent: ModalInteractionEvent

    @Mock
    private lateinit var member: Member

    @Mock
    private lateinit var moderatorUser: User

    @Mock
    private lateinit var guild: Guild

    @Mock
    private lateinit var replyAction: ReplyCallbackAction

    @Mock
    private lateinit var modalAction: ModalCallbackAction

    @Mock
    private lateinit var messageEditAction: MessageEditCallbackAction

    @Mock
    private lateinit var reasonValue: ModalMapping

    @Mock
    private lateinit var jda: JDA

    private lateinit var command: RevokeWarnPointsCommand

    @BeforeEach
    fun setUp() {
        command = RevokeWarnPointsCommand(guildWarnPointsService)
    }

    @Test
    fun `command data exposes direct and guided subcommands`() {
        val commandData = command.getCommandsData().single()

        assertEquals("revokewarnpoints", commandData.name)
        assertEquals(listOf("direct", "guided"), commandData.subcommands.map { it.name })
    }

    @Test
    fun `direct revoke removes the targeted warn point`() {
        val warnPointId = UUID.fromString("11111111-1111-1111-1111-111111111111")
        val warnPoint = warnPoint(warnPointId)

        whenever(slashEvent.name).thenReturn("revokewarnpoints")
        whenever(slashEvent.subcommandName).thenReturn("direct")
        whenever(slashEvent.member).thenReturn(member)
        whenever(slashEvent.guild).thenReturn(guild)
        whenever(slashEvent.jda).thenReturn(jda)
        whenever(member.hasPermission(Permission.KICK_MEMBERS)).thenReturn(true)
        whenever(slashEvent.getOption("user")).thenReturn(optionLong(99L))
        whenever(slashEvent.getOption("warn_point_id")).thenReturn(stringOption(warnPointId.toString()))
        whenever(slashEvent.getOption("reason")).thenReturn(stringOption("Spamming"))
        whenever(guild.idLong).thenReturn(1L)
        whenever(jda.registeredListeners).thenReturn(emptyList())
        whenever(guildWarnPointsService.getWarningById(warnPointId)).thenReturn(warnPoint)
        whenever(slashEvent.reply(any<String>())).thenReturn(replyAction)
        whenever(replyAction.setEphemeral(true)).thenReturn(replyAction)

        command.onSlashCommandInteraction(slashEvent)

        verify(guildWarnPointsService).revokePoint(warnPointId)
        verify(slashEvent).reply("Revoked 2 warn point(s) from <@99>. Reason: Spamming")
    }

    @Test
    fun `guided select opens a reason modal`() {
        val warnPointId = UUID.fromString("22222222-2222-2222-2222-222222222222")
        val warnPoint = warnPoint(warnPointId)

        whenever(selectEvent.componentId).thenReturn("revokewarnpoints-select:12:99:0")
        whenever(selectEvent.member).thenReturn(member)
        whenever(selectEvent.guild).thenReturn(guild)
        whenever(selectEvent.user).thenReturn(moderatorUser)
        whenever(moderatorUser.idLong).thenReturn(12L)
        whenever(member.hasPermission(Permission.KICK_MEMBERS)).thenReturn(true)
        whenever(selectEvent.values).thenReturn(listOf(warnPointId.toString()))
        whenever(guild.idLong).thenReturn(1L)
        whenever(guildWarnPointsService.getWarningById(warnPointId)).thenReturn(warnPoint)
        whenever(selectEvent.replyModal(any())).thenReturn(modalAction)

        command.onStringSelectInteraction(selectEvent)

        verify(selectEvent).replyModal(argThat { id == "revokewarnpoints-reason:12:99:$warnPointId" })
    }

    @Test
    fun `modal submission revokes the selected warn point`() {
        val warnPointId = UUID.fromString("33333333-3333-3333-3333-333333333333")
        val warnPoint = warnPoint(warnPointId)

        whenever(modalEvent.modalId).thenReturn("revokewarnpoints-reason:12:99:$warnPointId")
        whenever(modalEvent.member).thenReturn(member)
        whenever(modalEvent.guild).thenReturn(guild)
        whenever(modalEvent.jda).thenReturn(jda)
        whenever(modalEvent.user).thenReturn(moderatorUser)
        whenever(moderatorUser.idLong).thenReturn(12L)
        whenever(member.hasPermission(Permission.KICK_MEMBERS)).thenReturn(true)
        whenever(modalEvent.getValue("reason")).thenReturn(reasonValue)
        whenever(reasonValue.asString).thenReturn("Off-topic")
        whenever(guild.idLong).thenReturn(1L)
        whenever(jda.registeredListeners).thenReturn(emptyList())
        whenever(guildWarnPointsService.getWarningById(warnPointId)).thenReturn(warnPoint)
        whenever(modalEvent.reply(any<String>())).thenReturn(replyAction)
        whenever(replyAction.setEphemeral(true)).thenReturn(replyAction)

        command.onModalInteraction(modalEvent)

        verify(guildWarnPointsService).revokePoint(warnPointId)
        verify(modalEvent).reply("Revoked 2 warn point(s) from <@99>. Reason: Off-topic")
    }

    @Test
    fun `direct revoke rejects unknown warn point ids`() {
        val warnPointId = UUID.fromString("44444444-4444-4444-4444-444444444444")

        whenever(slashEvent.name).thenReturn("revokewarnpoints")
        whenever(slashEvent.subcommandName).thenReturn("direct")
        whenever(slashEvent.member).thenReturn(member)
        whenever(slashEvent.guild).thenReturn(guild)
        whenever(member.hasPermission(Permission.KICK_MEMBERS)).thenReturn(true)
        whenever(slashEvent.getOption("user")).thenReturn(optionLong(99L))
        whenever(slashEvent.getOption("warn_point_id")).thenReturn(stringOption(warnPointId.toString()))
        whenever(slashEvent.getOption("reason")).thenReturn(stringOption("Spamming"))
        whenever(guild.idLong).thenReturn(1L)
        whenever(guildWarnPointsService.getWarningById(warnPointId)).thenReturn(null)
        whenever(slashEvent.reply(any<String>())).thenReturn(replyAction)
        whenever(replyAction.setEphemeral(true)).thenReturn(replyAction)

        command.onSlashCommandInteraction(slashEvent)

        verify(guildWarnPointsService, never()).revokePoint(any())
        verify(slashEvent).reply("No warn point found with ID `$warnPointId` for this user.")
    }

    @Test
    fun `guided revoke paginates newest warnings first`() {
        val warnings = warningSeries(26)

        whenever(slashEvent.name).thenReturn("revokewarnpoints")
        whenever(slashEvent.subcommandName).thenReturn("guided")
        whenever(slashEvent.member).thenReturn(member)
        whenever(slashEvent.guild).thenReturn(guild)
        whenever(member.hasPermission(Permission.KICK_MEMBERS)).thenReturn(true)
        whenever(slashEvent.getOption("user")).thenReturn(optionLong(99L))
        whenever(guild.idLong).thenReturn(1L)
        whenever(guildWarnPointsService.getActiveWarnings(1L, 99L)).thenReturn(warnings)
        whenever(slashEvent.reply(any<MessageCreateData>())).thenReturn(replyAction)
        whenever(replyAction.setEphemeral(true)).thenReturn(replyAction)

        command.onSlashCommandInteraction(slashEvent)

        val messageCaptor = argumentCaptor<MessageCreateData>()
        verify(slashEvent).reply(messageCaptor.capture())
        val message = messageCaptor.firstValue
        val components = message.toData().getArray("components")
        val firstRow = components.getObject(0).getArray("components")
        val secondRow = components.getObject(1).getArray("components")
        val options = firstRow.getObject(0).getArray("options")

        assertEquals("Select a warn point to revoke from <@99>. Page 1/2.", message.content)
        assertEquals(2, components.length())
        assertEquals(1, firstRow.length())
        assertEquals(25, options.length())
        assertTrue(options.getObject(0).getString("label").contains("Warning 25"))
        assertTrue(options.getObject(24).getString("label").contains("Warning 1"))
        assertEquals("Next", secondRow.getObject(0).getString("label"))
    }

    @Test
    fun `guided revoke allows users that are no longer guild members`() {
        val warnings = warningSeries(1)

        whenever(slashEvent.name).thenReturn("revokewarnpoints")
        whenever(slashEvent.subcommandName).thenReturn("guided")
        whenever(slashEvent.member).thenReturn(member)
        whenever(slashEvent.guild).thenReturn(guild)
        whenever(member.hasPermission(Permission.KICK_MEMBERS)).thenReturn(true)
        whenever(slashEvent.getOption("user")).thenReturn(optionLong(99L))
        whenever(guild.idLong).thenReturn(1L)
        whenever(guildWarnPointsService.getActiveWarnings(1L, 99L)).thenReturn(warnings)
        whenever(slashEvent.reply(any<MessageCreateData>())).thenReturn(replyAction)
        whenever(replyAction.setEphemeral(true)).thenReturn(replyAction)

        command.onSlashCommandInteraction(slashEvent)

        verify(member, never()).canInteract(any<Member>())
        val messageCaptor = argumentCaptor<MessageCreateData>()
        verify(slashEvent).reply(messageCaptor.capture())
        assertEquals("Select a warn point to revoke from <@99>.", messageCaptor.firstValue.content)
    }

    @Test
    fun `guided revoke page button shows the requested page`() {
        val warnings = warningSeries(26)

        whenever(buttonEvent.componentId).thenReturn("revokewarnpoints-page:12:99:1")
        whenever(buttonEvent.member).thenReturn(member)
        whenever(buttonEvent.guild).thenReturn(guild)
        whenever(buttonEvent.user).thenReturn(moderatorUser)
        whenever(moderatorUser.idLong).thenReturn(12L)
        whenever(member.hasPermission(Permission.KICK_MEMBERS)).thenReturn(true)
        whenever(guild.idLong).thenReturn(1L)
        whenever(guildWarnPointsService.getActiveWarnings(1L, 99L)).thenReturn(warnings)
        whenever(buttonEvent.editMessage(any<String>())).thenReturn(messageEditAction)
        whenever(messageEditAction.setComponents(any<List<ActionRow>>())).thenReturn(messageEditAction)

        command.onButtonInteraction(buttonEvent)

        val messageCaptor = argumentCaptor<String>()
        verify(buttonEvent).editMessage(messageCaptor.capture())
        assertEquals("Select a warn point to revoke from <@99>. Page 2/2.", messageCaptor.firstValue)

        val rowsCaptor = argumentCaptor<List<ActionRow>>()
        verify(messageEditAction).setComponents(rowsCaptor.capture())
        val rows = rowsCaptor.firstValue
        val selectMenu = rows[0].components.single() as StringSelectMenu
        val previousButton = rows[1].buttons.single()

        assertEquals(2, rows.size)
        assertEquals(1, selectMenu.options.size)
        assertTrue(selectMenu.options.single().label.contains("Warning 0"))
        assertEquals("Previous", previousButton.label)
        assertEquals("revokewarnpoints-page:12:99:0", previousButton.customId)
    }

    @Test
    fun `guided revoke page button clears stale menus`() {
        whenever(buttonEvent.componentId).thenReturn("revokewarnpoints-page:12:99:1")
        whenever(buttonEvent.member).thenReturn(member)
        whenever(buttonEvent.guild).thenReturn(guild)
        whenever(buttonEvent.user).thenReturn(moderatorUser)
        whenever(moderatorUser.idLong).thenReturn(12L)
        whenever(member.hasPermission(Permission.KICK_MEMBERS)).thenReturn(true)
        whenever(guild.idLong).thenReturn(1L)
        whenever(guildWarnPointsService.getActiveWarnings(1L, 99L)).thenReturn(warningSeries(25))
        whenever(buttonEvent.editMessage(any<String>())).thenReturn(messageEditAction)
        whenever(messageEditAction.setComponents(any<List<ActionRow>>())).thenReturn(messageEditAction)

        command.onButtonInteraction(buttonEvent)

        verify(buttonEvent).editMessage("This warn point menu is out of date. Run the command again.")
        val rowsCaptor = argumentCaptor<List<ActionRow>>()
        verify(messageEditAction).setComponents(rowsCaptor.capture())
        assertTrue(rowsCaptor.firstValue.isEmpty())
    }

    private fun warnPoint(
        id: UUID,
        points: Int = 2,
        reason: String = "Spamming",
        creationDate: OffsetDateTime = OffsetDateTime.now()
    ): GuildWarnPoint {
        return GuildWarnPoint(
            userId = 99L,
            guildId = 1L,
            id = id,
            points = points,
            creatorId = 12L,
            reason = reason,
            creationDate = creationDate,
            expireDate = OffsetDateTime.now().plusDays(2)
        )
    }

    private fun warningSeries(count: Int): List<GuildWarnPoint> {
        val baseCreationDate = OffsetDateTime.parse("2026-01-01T00:00:00Z")
        return (0 until count).map { index ->
            warnPoint(
                id = UUID.fromString("00000000-0000-0000-0000-${index.toString().padStart(12, '0')}"),
                points = index + 1,
                reason = "Warning $index",
                creationDate = baseCreationDate.plusMinutes(index.toLong())
            )
        }
    }

    private fun optionLong(value: Long): OptionMapping {
        val data = DataObject.empty()
            .put("type", OptionType.USER.key)
            .put("name", "value")
            .put("value", value)
        val resolvedClass = Class.forName("gnu.trove.map.hash.TLongObjectHashMap")
        val resolved = resolvedClass.getConstructor().newInstance()
        resolvedClass.getMethod("put", Long::class.javaPrimitiveType, Any::class.java).invoke(
            resolved,
            value,
            mock<User>()
        )

        val constructor = OptionMapping::class.java.getConstructor(
            DataObject::class.java,
            Class.forName("gnu.trove.map.TLongObjectMap"),
            JDA::class.java,
            Guild::class.java
        )

        @Suppress("UNCHECKED_CAST")
        return constructor.newInstance(data, resolved, null, null) as OptionMapping
    }

    private fun stringOption(value: String): OptionMapping {
        return optionMapping(OptionType.STRING, value)
    }

    private fun optionMapping(type: OptionType, value: Any): OptionMapping {
        val data = DataObject.empty()
            .put("type", type.key)
            .put("name", "value")
            .put("value", value)

        val constructor = OptionMapping::class.java.getConstructor(
            DataObject::class.java,
            Class.forName("gnu.trove.map.TLongObjectMap"),
            JDA::class.java,
            Guild::class.java
        )

        @Suppress("UNCHECKED_CAST")
        return constructor.newInstance(data, null, null, null) as OptionMapping
    }
}
