package be.duncanc.discordmodbot.utility

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@ExtendWith(MockitoExtension::class)
class HelpCommandTest {
    @Mock
    private lateinit var slashEvent: SlashCommandInteractionEvent

    private lateinit var command: HelpCommand

    @BeforeEach
    fun setUp() {
        command = HelpCommand()
    }

    @Test
    fun `command name filter - non-matching name returns early`() {
        whenever(slashEvent.name).thenReturn("othercommand")

        command.onSlashCommandInteraction(slashEvent)

        verify(slashEvent, never()).reply(any<String>())
    }

}
