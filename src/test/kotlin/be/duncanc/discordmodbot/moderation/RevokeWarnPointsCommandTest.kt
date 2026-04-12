package be.duncanc.discordmodbot.moderation

import be.duncanc.discordmodbot.moderation.persistence.GuildWarnPoint
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.Member
import net.dv8tion.jda.api.entities.User
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData
import net.dv8tion.jda.api.interactions.commands.OptionMapping
import net.dv8tion.jda.api.interactions.commands.OptionType
import net.dv8tion.jda.api.interactions.modals.ModalMapping
import net.dv8tion.jda.api.requests.restaction.interactions.ModalCallbackAction
import net.dv8tion.jda.api.requests.restaction.interactions.ReplyCallbackAction
import net.dv8tion.jda.api.utils.data.DataObject
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.any
import org.mockito.kotlin.argThat
import org.mockito.kotlin.verify
import org.mockito.kotlin.never
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

        assert(commandData.name == "revokewarnpoints")
        assert(commandData.subcommands.map { it.name } == listOf("direct", "guided"))
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
        whenever(member.idLong).thenReturn(12L)
        whenever(slashEvent.getOption("user")).thenReturn(optionLong(99L))
        whenever(slashEvent.getOption("warn_point_id")).thenReturn(optionString(warnPointId.toString()))
        whenever(slashEvent.getOption("reason")).thenReturn(optionString("Spamming"))
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

        whenever(selectEvent.componentId).thenReturn("revokewarnpoints-select:12:99")
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
        whenever(slashEvent.getOption("warn_point_id")).thenReturn(optionString(warnPointId.toString()))
        whenever(slashEvent.getOption("reason")).thenReturn(optionString("Spamming"))
        whenever(guild.idLong).thenReturn(1L)
        whenever(guildWarnPointsService.getWarningById(warnPointId)).thenReturn(null)
        whenever(slashEvent.reply(any<String>())).thenReturn(replyAction)
        whenever(replyAction.setEphemeral(true)).thenReturn(replyAction)

        command.onSlashCommandInteraction(slashEvent)

        verify(guildWarnPointsService, never()).revokePoint(any())
        verify(slashEvent).reply("No warn point found with ID `$warnPointId` for this user.")
    }

    private fun warnPoint(id: UUID): GuildWarnPoint {
        return GuildWarnPoint(
            userId = 99L,
            guildId = 1L,
            id = id,
            points = 2,
            creatorId = 12L,
            reason = "Spamming",
            expireDate = OffsetDateTime.now().plusDays(2)
        )
    }

    private fun optionLong(value: Long): OptionMapping {
        return optionMapping(OptionType.INTEGER, value)
    }

    private fun optionString(value: String): OptionMapping {
        return optionMapping(OptionType.INTEGER, value)
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
