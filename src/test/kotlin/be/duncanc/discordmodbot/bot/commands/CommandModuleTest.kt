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
import org.mockito.Mockito.*
import org.mockito.junit.jupiter.MockitoExtension

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
        val messageReceivedEvent: MessageReceivedEvent = mock(MessageReceivedEvent::class.java)
        val message: Message = mock(Message::class.java)
        `when`(messageReceivedEvent.message).thenReturn(message)
        val messageContent = "!${commandModule.aliases[0]} <@1>"
        `when`(message.contentRaw).thenReturn(messageContent)
        val user: User = mock(User::class.java)
        `when`(messageReceivedEvent.author).thenReturn(user)
        val jda: JDA = mock(JDA::class.java)
        `when`(messageReceivedEvent.jda).thenReturn(jda)
        `when`(message.contentDisplay).thenReturn(messageContent)
        val selfUser: SelfUser = mock(SelfUser::class.java)
        `when`(jda.selfUser).thenReturn(selfUser)
        val messageChannel: MessageChannel = mock(MessageChannel::class.java)
        `when`(messageReceivedEvent.channel).thenReturn(messageChannel)
        `when`(messageReceivedEvent.isFromType(ChannelType.TEXT)).thenReturn(true)
        @Suppress("UNCHECKED_CAST")
        val auditableRestAction = mock(AuditableRestAction::class.java) as AuditableRestAction<Void>
        `when`(message.delete()).thenReturn(auditableRestAction)
        // Act
        commandModule.onMessageReceived(messageReceivedEvent)
        // Assert
        verify(commandModule).onMessageReceived(messageReceivedEvent)
        verify(commandModule, times(2)).userBlockService
    }

    override fun commandExec(event: MessageReceivedEvent, command: String, arguments: String?) {
    }
}
