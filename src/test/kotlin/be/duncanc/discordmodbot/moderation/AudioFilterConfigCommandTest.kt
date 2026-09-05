package be.duncanc.discordmodbot.moderation

import be.duncanc.discordmodbot.moderation.AudioFilterService.Companion.MAX_TIMEOUT_MINUTES
import be.duncanc.discordmodbot.moderation.persistence.AudioFilterSettings
import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.Member
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.interactions.InteractionContextType
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions
import net.dv8tion.jda.api.interactions.commands.OptionMapping
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData
import net.dv8tion.jda.api.requests.restaction.interactions.ReplyCallbackAction
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.*

@ExtendWith(MockitoExtension::class)
class AudioFilterConfigCommandTest {
    @Mock
    private lateinit var audioFilterService: AudioFilterService

    @Mock
    private lateinit var slashEvent: SlashCommandInteractionEvent

    @Mock
    private lateinit var guild: Guild

    @Mock
    private lateinit var member: Member

    @Mock
    private lateinit var replyAction: ReplyCallbackAction

    @Mock
    private lateinit var timeoutOption: OptionMapping

    private lateinit var command: AudioFilterConfigCommand

    @BeforeEach
    fun setUp() {
        command = AudioFilterConfigCommand(audioFilterService)
    }

    @Test
    fun `non-matching command name returns early`() {
        whenever(slashEvent.name).thenReturn("othercommand")

        command.onSlashCommandInteraction(slashEvent)

        verify(slashEvent, never()).reply(any<String>())
    }

    @Test
    fun `missing member returns guild error`() {
        stubSlashCommandContext(member = null)

        command.onSlashCommandInteraction(slashEvent)

        verify(slashEvent).reply("This command only works in a guild.")
    }

    @Test
    fun `missing administrator permission returns error`() {
        stubSlashCommandContext()
        whenever(member.hasPermission(Permission.ADMINISTRATOR)).thenReturn(false)

        command.onSlashCommandInteraction(slashEvent)

        verify(slashEvent).reply("You need administrator permission to use this command.")
    }

    @Test
    fun `show displays disabled settings`() {
        stubAuthorizedSlashCommand("show")
        whenever(guild.name).thenReturn("Test Guild")

        command.onSlashCommandInteraction(slashEvent)

        val reply = captureReply()
        assertTrue(reply.contains("Audio file filter settings for Test Guild"))
        assertTrue(reply.contains("- Filter: Disabled"))
        assertTrue(reply.contains("- Timeout: None"))
    }

    @Test
    fun `show displays enabled settings with timeout`() {
        stubAuthorizedSlashCommand("show")
        whenever(guild.name).thenReturn("Test Guild")
        whenever(audioFilterService.getSettings(1L)).thenReturn(AudioFilterSettings(1L, 15L))

        command.onSlashCommandInteraction(slashEvent)

        val reply = captureReply()
        assertTrue(reply.contains("- Filter: Enabled"))
        assertTrue(reply.contains("- Timeout: 15 minutes"))
    }

    @Test
    fun `enable enables the filter without timeout`() {
        stubAuthorizedSlashCommand("enable")
        whenever(audioFilterService.enableFilter(1L, null)).thenReturn(AudioFilterSettings(1L, null))

        command.onSlashCommandInteraction(slashEvent)

        verify(audioFilterService).enableFilter(1L, null)
        assertEquals("Audio file filter enabled. Posted audio files will be deleted.", captureReply())
    }

    @Test
    fun `enable enables the filter with timeout`() {
        stubAuthorizedSlashCommand("enable")
        whenever(slashEvent.getOption("timeout")).thenReturn(timeoutOption)
        whenever(timeoutOption.asLong).thenReturn(30L)
        whenever(audioFilterService.enableFilter(1L, 30L)).thenReturn(AudioFilterSettings(1L, 30L))

        command.onSlashCommandInteraction(slashEvent)

        verify(audioFilterService).enableFilter(1L, 30L)
        assertEquals(
            "Audio file filter enabled. Posted audio files will be deleted and the poster will be timed out for 30 minutes.",
            captureReply()
        )
    }

    @Test
    fun `disable disables the filter`() {
        stubAuthorizedSlashCommand("disable")

        command.onSlashCommandInteraction(slashEvent)

        verify(audioFilterService).disableFilter(1L)
        assertEquals("Audio file filter disabled.", captureReply())
    }

    @Test
    fun `timeout errors when the filter is not enabled`() {
        stubAuthorizedSlashCommand("timeout")
        whenever(slashEvent.getOption("timeout")).thenReturn(timeoutOption)
        whenever(timeoutOption.asLong).thenReturn(45L)
        whenever(audioFilterService.setTimeout(1L, 45L)).thenReturn(null)

        command.onSlashCommandInteraction(slashEvent)

        assertEquals("The audio file filter is not enabled. Use /audiofilter enable first.", captureReply())
    }

    @Test
    fun `timeout updates the configured timeout`() {
        stubAuthorizedSlashCommand("timeout")
        whenever(slashEvent.getOption("timeout")).thenReturn(timeoutOption)
        whenever(timeoutOption.asLong).thenReturn(45L)
        whenever(audioFilterService.setTimeout(1L, 45L)).thenReturn(AudioFilterSettings(1L, 45L))

        command.onSlashCommandInteraction(slashEvent)

        assertEquals("Members posting audio files will now be timed out for 45 minutes.", captureReply())
    }

    @Test
    fun `timeout removes the configured timeout`() {
        stubAuthorizedSlashCommand("timeout")
        whenever(audioFilterService.setTimeout(1L, null)).thenReturn(AudioFilterSettings(1L, null))

        command.onSlashCommandInteraction(slashEvent)

        assertEquals("Timeout removed. Posted audio files will only be deleted.", captureReply())
    }

    @Test
    fun `command data exposes expected subcommands and metadata`() {
        val commandData = command.getCommandsData().single()

        assertEquals("audiofilter", commandData.name)
        assertEquals(setOf(InteractionContextType.GUILD), commandData.contexts)
        assertEquals(DefaultMemberPermissions.enabledFor(Permission.ADMINISTRATOR), commandData.defaultPermissions)
        assertEquals(listOf("show", "enable", "disable", "timeout"), commandData.subcommands.map(SubcommandData::getName))

        val enableOption = commandData.subcommands.single { it.name == "enable" }.options.single()
        assertEquals(false, enableOption.isRequired)
        assertEquals(1L, enableOption.minValue)
        assertEquals(MAX_TIMEOUT_MINUTES, enableOption.maxValue)

        val timeoutOptionData = commandData.subcommands.single { it.name == "timeout" }.options.single()
        assertEquals(true, timeoutOptionData.isRequired)
    }

    private fun captureReply(): String {
        val replyCaptor = argumentCaptor<String>()
        verify(slashEvent).reply(replyCaptor.capture())
        return replyCaptor.firstValue
    }

    private fun stubSlashCommandContext(member: Member? = this.member) {
        whenever(slashEvent.name).thenReturn("audiofilter")
        whenever(slashEvent.guild).thenReturn(guild)
        whenever(slashEvent.member).thenReturn(member)
        whenever(slashEvent.reply(any<String>())).thenReturn(replyAction)
        whenever(replyAction.setEphemeral(true)).thenReturn(replyAction)
    }

    private fun stubAuthorizedSlashCommand(subcommandName: String) {
        stubSlashCommandContext()
        whenever(guild.idLong).thenReturn(1L)
        whenever(member.hasPermission(Permission.ADMINISTRATOR)).thenReturn(true)
        whenever(slashEvent.subcommandName).thenReturn(subcommandName)
    }
}
