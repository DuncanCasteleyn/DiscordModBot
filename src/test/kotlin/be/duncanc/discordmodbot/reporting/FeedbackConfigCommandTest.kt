package be.duncanc.discordmodbot.reporting

import be.duncanc.discordmodbot.reporting.persistence.ReportChannel
import be.duncanc.discordmodbot.reporting.persistence.ReportChannelRepository
import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.Member
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel
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
import java.util.*

@ExtendWith(MockitoExtension::class)
class FeedbackConfigCommandTest {
    @Mock
    private lateinit var reportChannelRepository: ReportChannelRepository

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

    private lateinit var command: TestFeedbackConfigCommand

    @BeforeEach
    fun setUp() {
        command = TestFeedbackConfigCommand(reportChannelRepository)
    }

    @Test
    fun `missing manage channel permission returns error`() {
        stubSlashCommandContext()
        whenever(member.hasPermission(Permission.MANAGE_CHANNEL)).thenReturn(false)

        command.onSlashCommandInteraction(slashEvent)

        verify(slashEvent).reply("You need manage channel permission to use this command.")
    }

    @Test
    fun `show displays disabled state`() {
        stubAuthorizedSlashCommand("show")
        whenever(guild.name).thenReturn("Test Guild")
        whenever(reportChannelRepository.findById(1L)).thenReturn(Optional.empty())

        command.onSlashCommandInteraction(slashEvent)

        val replyCaptor = argumentCaptor<String>()
        verify(slashEvent).reply(replyCaptor.capture())
        assertEquals(true, replyCaptor.firstValue.contains("- Feedback channel: Disabled"))
    }

    @Test
    fun `set channel stores selected channel`() {
        stubAuthorizedSlashCommand("set-channel")
        whenever(textChannel.idLong).thenReturn(11L)
        whenever(textChannel.asMention).thenReturn("<#11>")
        command.selectedChannel = textChannel

        command.onSlashCommandInteraction(slashEvent)

        val reportChannelCaptor = argumentCaptor<ReportChannel>()
        verify(reportChannelRepository).save(reportChannelCaptor.capture())
        assertEquals(11L, reportChannelCaptor.firstValue.textChannelId)
        verify(slashEvent).reply("Feedback channel set to <#11>.")
    }

    @Test
    fun `disable removes feedback configuration`() {
        stubAuthorizedSlashCommand("disable")

        command.onSlashCommandInteraction(slashEvent)

        verify(reportChannelRepository).deleteById(1L)
        verify(slashEvent).reply("Feedback disabled.")
    }

    @Test
    fun `command data exposes expected subcommands`() {
        val commandData = command.getCommandsData().single()

        assertEquals("feedbackconfig", commandData.name)
        assertEquals(setOf(InteractionContextType.GUILD), commandData.contexts)
        assertEquals(listOf("show", "set-channel", "disable"), commandData.subcommands.map(SubcommandData::getName))
    }

    private fun stubSlashCommandContext() {
        whenever(slashEvent.name).thenReturn("feedbackconfig")
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

    private class TestFeedbackConfigCommand(
        reportChannelRepository: ReportChannelRepository
    ) : FeedbackConfigCommand(reportChannelRepository) {
        var selectedChannel: TextChannel? = null

        override fun getRequiredTextChannel(event: SlashCommandInteractionEvent): TextChannel? {
            return selectedChannel ?: super.getRequiredTextChannel(event)
        }
    }
}
