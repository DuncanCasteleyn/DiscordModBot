package be.duncanc.discordmodbot.narou.novel.api

import be.duncanc.discordmodbot.narou.novel.api.persistence.NarouNovelAlertSettings
import be.duncanc.discordmodbot.narou.novel.api.persistence.NarouNovelAlertSettingsRepository
import be.duncanc.discordmodbot.narou.novel.api.persistence.NarouNovelPendingAlertRepository
import be.duncanc.discordmodbot.narou.novel.api.persistence.NarouNovelSnapshot
import be.duncanc.discordmodbot.narou.novel.api.persistence.NarouNovelSnapshotRepository
import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.Member
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel
import net.dv8tion.jda.api.events.guild.GuildLeaveEvent
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.interactions.InteractionContextType
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData
import net.dv8tion.jda.api.requests.restaction.interactions.ReplyCallbackAction
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.Optional

@ExtendWith(MockitoExtension::class)
class NarouNovelConfigCommandTest {
    @Mock
    private lateinit var narouNovelAlertSettingsRepository: NarouNovelAlertSettingsRepository

    @Mock
    private lateinit var narouNovelSnapshotRepository: NarouNovelSnapshotRepository

    @Mock
    private lateinit var narouNovelPendingAlertRepository: NarouNovelPendingAlertRepository

    @Mock
    private lateinit var slashEvent: SlashCommandInteractionEvent

    @Mock
    private lateinit var guild: Guild

    @Mock
    private lateinit var member: Member

    @Mock
    private lateinit var textChannel: TextChannel

    @Mock
    private lateinit var replyAction: ReplyCallbackAction

    @Mock
    private lateinit var guildLeaveEvent: GuildLeaveEvent

    private lateinit var command: TestNarouNovelConfigCommand

    @BeforeEach
    fun setUp() {
        command = TestNarouNovelConfigCommand(
            narouNovelAlertSettingsRepository,
            narouNovelPendingAlertRepository,
            narouNovelSnapshotRepository
        )
    }

    @Test
    fun `missing manage channel permission returns error`() {
        stubSlashCommandContext()
        whenever(member.hasPermission(Permission.MANAGE_CHANNEL)).thenReturn(false)

        command.onSlashCommandInteraction(slashEvent)

        verify(slashEvent).reply("You need manage channel permission to use this command.")
    }

    @Test
    fun `show displays disabled state with default threshold`() {
        stubAuthorizedSlashCommand("show")
        whenever(guild.name).thenReturn("Test Guild")
        whenever(narouNovelAlertSettingsRepository.findById(1L)).thenReturn(Optional.empty())

        command.onSlashCommandInteraction(slashEvent)

        val replyCaptor = argumentCaptor<String>()
        verify(slashEvent).reply(replyCaptor.capture())
        assertEquals(true, replyCaptor.firstValue.contains("- Alert channel: Disabled"))
        assertEquals(true, replyCaptor.firstValue.contains("- Published novel growth threshold: 1000"))
        assertEquals(true, replyCaptor.firstValue.contains("- Author profile prediction threshold: 1000"))
    }

    @Test
    fun `set channel stores selected channel and initializes baselines from snapshot`() {
        stubAuthorizedSlashCommand("set-channel")
        whenever(textChannel.idLong).thenReturn(11L)
        whenever(textChannel.asMention).thenReturn("<#11>")
        whenever(narouNovelAlertSettingsRepository.findById(1L)).thenReturn(Optional.empty())
        whenever(narouNovelSnapshotRepository.findById(NarouNovelPollingService.NOVEL_CODE)).thenReturn(
            Optional.of(
                NarouNovelSnapshot(
                    ncode = NarouNovelPollingService.NOVEL_CODE,
                    generalLastup = "2026-05-15 07:00:00",
                    generalAllNo = 778,
                    length = 9_445_269,
                    authorProfileLength = 9_876_000,
                    time = 18_891,
                    novelUpdatedAt = "2026-05-15 07:00:36",
                    updatedAt = "2026-05-15 20:14:23"
                )
            )
        )
        command.selectedChannel = textChannel

        command.onSlashCommandInteraction(slashEvent)

        val settingsCaptor = argumentCaptor<NarouNovelAlertSettings>()
        verify(narouNovelAlertSettingsRepository).save(settingsCaptor.capture())
        assertEquals(11L, settingsCaptor.firstValue.channelId)
        assertEquals(9_445_269L, settingsCaptor.firstValue.lastAlertedLength)
        assertEquals(9_876_000L, settingsCaptor.firstValue.lastAlertedAuthorProfileLength)
        assertEquals(778, settingsCaptor.firstValue.lastAlertedGeneralAllNo)
        verify(slashEvent).reply("Narou novel alerts will be sent to <#11>.")
    }

    @Test
    fun `set threshold rejects small values`() {
        stubSlashCommandContext()
        whenever(member.hasPermission(Permission.MANAGE_CHANNEL)).thenReturn(true)
        whenever(slashEvent.subcommandName).thenReturn("set-threshold")
        command.threshold = 49L

        command.onSlashCommandInteraction(slashEvent)

        verify(slashEvent).reply("The length threshold must be at least 50.")
    }

    @Test
    fun `set threshold stores configured value`() {
        stubAuthorizedSlashCommand("set-threshold")
        command.threshold = 500L
        whenever(narouNovelAlertSettingsRepository.findById(1L)).thenReturn(Optional.empty())
        whenever(narouNovelSnapshotRepository.findById(NarouNovelPollingService.NOVEL_CODE)).thenReturn(Optional.empty())

        command.onSlashCommandInteraction(slashEvent)

        val settingsCaptor = argumentCaptor<NarouNovelAlertSettings>()
        verify(narouNovelAlertSettingsRepository).save(settingsCaptor.capture())
        assertEquals(500L, settingsCaptor.firstValue.lengthThreshold)
        verify(slashEvent).reply("Narou novel length alerts now require at least 500 characters.")
    }

    @Test
    fun `set prediction threshold stores configured value`() {
        stubAuthorizedSlashCommand("set-prediction-threshold")
        command.threshold = 750L
        whenever(narouNovelAlertSettingsRepository.findById(1L)).thenReturn(Optional.empty())
        whenever(narouNovelSnapshotRepository.findById(NarouNovelPollingService.NOVEL_CODE)).thenReturn(Optional.empty())

        command.onSlashCommandInteraction(slashEvent)

        val settingsCaptor = argumentCaptor<NarouNovelAlertSettings>()
        verify(narouNovelAlertSettingsRepository).save(settingsCaptor.capture())
        assertEquals(750L, settingsCaptor.firstValue.predictionLengthThreshold)
        verify(slashEvent).reply("Narou prediction alerts now require at least 750 characters of author profile growth.")
    }

    @Test
    fun `disable removes narou novel configuration`() {
        stubAuthorizedSlashCommand("disable")

        command.onSlashCommandInteraction(slashEvent)

        verify(narouNovelAlertSettingsRepository).deleteById(1L)
        verify(narouNovelPendingAlertRepository).deleteById(1L)
        verify(slashEvent).reply("Narou novel alerts disabled.")
    }

    @Test
    fun `guild leave removes narou novel configuration and pending alert`() {
        whenever(guild.idLong).thenReturn(1L)
        whenever(guildLeaveEvent.guild).thenReturn(guild)

        command.onGuildLeave(guildLeaveEvent)

        verify(narouNovelAlertSettingsRepository).deleteById(1L)
        verify(narouNovelPendingAlertRepository).deleteById(1L)
    }

    @Test
    fun `command data exposes expected subcommands`() {
        val commandData = command.getCommandsData().single()

        assertEquals("narounovelapi", commandData.name)
        assertEquals(setOf(InteractionContextType.GUILD), commandData.contexts)
        assertEquals(
            listOf("show", "set-channel", "set-threshold", "set-prediction-threshold", "disable"),
            commandData.subcommands.map(SubcommandData::getName)
        )
    }

    private fun stubSlashCommandContext() {
        whenever(slashEvent.name).thenReturn("narounovelapi")
        whenever(slashEvent.guild).thenReturn(guild)
        whenever(slashEvent.member).thenReturn(member)
        whenever(slashEvent.reply(any<String>())).thenReturn(replyAction)
        whenever(replyAction.setEphemeral(true)).thenReturn(replyAction)
    }

    private fun stubAuthorizedSlashCommand(subcommandName: String) {
        stubSlashCommandContext()
        whenever(guild.idLong).thenReturn(1L)
        whenever(member.hasPermission(Permission.MANAGE_CHANNEL)).thenReturn(true)
        whenever(slashEvent.subcommandName).thenReturn(subcommandName)
    }

    private class TestNarouNovelConfigCommand(
        narouNovelAlertSettingsRepository: NarouNovelAlertSettingsRepository,
        narouNovelPendingAlertRepository: NarouNovelPendingAlertRepository,
        narouNovelSnapshotRepository: NarouNovelSnapshotRepository
    ) : NarouNovelConfigCommand(
        narouNovelAlertSettingsRepository,
        narouNovelPendingAlertRepository,
        narouNovelSnapshotRepository
    ) {
        var selectedChannel: TextChannel? = null
        var threshold: Long? = null

        override fun getRequiredTextChannel(event: SlashCommandInteractionEvent): TextChannel? {
            return selectedChannel ?: super.getRequiredTextChannel(event)
        }

        override fun getRequiredThreshold(event: SlashCommandInteractionEvent): Long? {
            return threshold ?: super.getRequiredThreshold(event)
        }
    }
}
