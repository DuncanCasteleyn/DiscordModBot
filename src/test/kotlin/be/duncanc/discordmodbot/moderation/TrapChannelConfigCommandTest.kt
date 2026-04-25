package be.duncanc.discordmodbot.moderation

import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.Member
import net.dv8tion.jda.api.entities.channel.ChannelType
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.interactions.InteractionContextType
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
class TrapChannelConfigCommandTest {
    @Mock
    private lateinit var trapChannelService: TrapChannelService

    @Mock
    private lateinit var slashEvent: SlashCommandInteractionEvent

    @Mock
    private lateinit var guild: Guild

    @Mock
    private lateinit var member: Member

    @Mock
    private lateinit var trapChannel: TextChannel

    @Mock
    private lateinit var replyAction: ReplyCallbackAction

    private lateinit var command: TestTrapChannelConfigCommand

    @BeforeEach
    fun setUp() {
        command = TestTrapChannelConfigCommand(trapChannelService)
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
    fun `show displays default settings`() {
        stubAuthorizedSlashCommand("show")
        whenever(guild.name).thenReturn("Test Guild")
        whenever(trapChannelService.getTrapChannelId(1L)).thenReturn(null)

        command.onSlashCommandInteraction(slashEvent)

        val replyCaptor = argumentCaptor<String>()
        verify(slashEvent).reply(replyCaptor.capture())
        val reply = replyCaptor.firstValue
        assertTrue(reply.contains("Trap channel settings for Test Guild"))
        assertTrue(reply.contains("- Trap channel: Not configured"))
        assertTrue(reply.contains("Ban, deletes recent messages, then unban after"))
    }

    @Test
    fun `set stores selected trap channel`() {
        stubAuthorizedSlashCommand("set")
        whenever(trapChannel.idLong).thenReturn(11L)
        whenever(trapChannel.asMention).thenReturn("<#11>")
        command.selectedChannel = trapChannel

        command.onSlashCommandInteraction(slashEvent)

        verify(trapChannelService).setTrapChannel(1L, 11L)
        verify(slashEvent).reply("Trap channel set to <#11>.")
    }

    @Test
    fun `clear removes trap channel configuration`() {
        stubAuthorizedSlashCommand("clear")

        command.onSlashCommandInteraction(slashEvent)

        verify(trapChannelService).clearTrapChannel(1L)
        verify(slashEvent).reply("Trap channel cleared.")
    }

    @Test
    fun `command data exposes expected subcommands and metadata`() {
        val commandData = command.getCommandsData().single()

        assertEquals("trapchannel", commandData.name)
        assertEquals(setOf(InteractionContextType.GUILD), commandData.contexts)
        assertEquals(listOf("show", "set", "clear"), commandData.subcommands.map(SubcommandData::getName))

        val channelOption = commandData.subcommands.single { it.name == "set" }.options.single()
        assertEquals(ChannelType.TEXT, channelOption.channelTypes.single())
        assertTrue(commandData.description.contains("trap channel"))
    }

    private fun stubSlashCommandContext(member: Member? = this.member) {
        whenever(slashEvent.name).thenReturn("trapchannel")
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

    private class TestTrapChannelConfigCommand(
        trapChannelService: TrapChannelService
    ) : TrapChannelConfigCommand(trapChannelService) {
        var selectedChannel: TextChannel? = null

        override fun getRequiredTextChannel(event: SlashCommandInteractionEvent): TextChannel? {
            return selectedChannel ?: super.getRequiredTextChannel(event)
        }
    }
}
