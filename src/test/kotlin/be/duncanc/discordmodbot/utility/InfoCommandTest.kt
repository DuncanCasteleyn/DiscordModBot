package be.duncanc.discordmodbot.utility

import net.dv8tion.jda.api.entities.MessageEmbed
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.requests.restaction.interactions.ReplyCallbackAction
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.*

@ExtendWith(MockitoExtension::class)
class InfoCommandTest {
    @Mock
    private lateinit var slashEvent: SlashCommandInteractionEvent

    @Mock
    private lateinit var replyAction: ReplyCallbackAction

    private lateinit var command: InfoCommand

    @BeforeEach
    fun setUp() {
        command = InfoCommand()
    }

    @Test
    fun `command name filter - non-matching name returns early`() {
        whenever(slashEvent.name).thenReturn("othercommand")

        command.onSlashCommandInteraction(slashEvent)

        verify(slashEvent, never()).replyEmbeds(any<MessageEmbed>())
    }

    @Test
    fun `success - replies with info embed`() {
        whenever(slashEvent.name).thenReturn("info")
        whenever(slashEvent.replyEmbeds(any<MessageEmbed>())).thenReturn(replyAction)
        whenever(replyAction.setEphemeral(true)).thenReturn(replyAction)

        command.onSlashCommandInteraction(slashEvent)

        verify(slashEvent).replyEmbeds(any<MessageEmbed>())
    }

    @Test
    fun `success - verifies embed title`() {
        whenever(slashEvent.name).thenReturn("info")
        val embedCapture = argumentCaptor<MessageEmbed>()
        whenever(slashEvent.replyEmbeds(embedCapture.capture())).thenReturn(replyAction)
        whenever(replyAction.setEphemeral(true)).thenReturn(replyAction)

        command.onSlashCommandInteraction(slashEvent)

        org.junit.jupiter.api.Assertions.assertEquals("Discord bot", embedCapture.firstValue.title)
    }
}
