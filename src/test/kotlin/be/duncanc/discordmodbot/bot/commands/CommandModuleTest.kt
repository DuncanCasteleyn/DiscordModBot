package be.duncanc.discordmodbot.bot.commands

import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.entities.*
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import net.dv8tion.jda.api.requests.restaction.AuditableRestAction
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.TestInstance.Lifecycle
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.*

@ExtendWith(MockitoExtension::class)
@TestInstance(Lifecycle.PER_CLASS)
internal open class CommandModuleTest : CommandModule(arrayOf("test"), null, null) {

    private lateinit var commandModule: CommandModuleTest

    @BeforeAll
    fun `Spy ourselves`() {
        commandModule = spy(this)
    }

    @Test
    fun `Command successfully executed`() {
        // Arrange
        val messageReceivedEvent: MessageReceivedEvent = mock()
        val message: Message = mock()
        whenever(messageReceivedEvent.message).thenReturn(message)
        val messageContent = "!${commandModule.aliases[0]} <@1>"
        whenever(message.contentRaw).thenReturn(messageContent)
        val user: User = mock()
        whenever(messageReceivedEvent.author).thenReturn(user)
        val jda: JDA = mock()
        whenever(messageReceivedEvent.jda).thenReturn(jda)
        whenever(message.contentDisplay).thenReturn(messageContent)
        val selfUser: SelfUser = mock()
        whenever(jda.selfUser).thenReturn(selfUser)
        val messageChannel: MessageChannel = mock()
        whenever(messageReceivedEvent.channel).thenReturn(messageChannel)
        whenever(messageReceivedEvent.isFromType(ChannelType.TEXT)).thenReturn(true)
        val auditableRestAction = mock<AuditableRestAction<Void>>()
        whenever(message.delete()).thenReturn(auditableRestAction)
        // Act
        commandModule.onMessageReceived(messageReceivedEvent)
        // Assert
        verify(commandModule).onMessageReceived(messageReceivedEvent)
        verify(commandModule, times(2)).userBlockService
    }

    override fun commandExec(event: MessageReceivedEvent, command: String, arguments: String?) {
    }
}
