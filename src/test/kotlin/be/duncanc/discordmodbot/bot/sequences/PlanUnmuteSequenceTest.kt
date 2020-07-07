package be.duncanc.discordmodbot.bot.sequences

import be.duncanc.discordmodbot.bot.services.GuildLogger
import be.duncanc.discordmodbot.data.entities.MuteRole
import be.duncanc.discordmodbot.data.repositories.MuteRolesRepository
import be.duncanc.discordmodbot.data.services.ScheduledUnmuteService
import com.nhaarman.mockitokotlin2.*
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.entities.*
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import net.dv8tion.jda.api.requests.restaction.MessageAction
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.boot.test.mock.mockito.SpyBean
import org.springframework.test.util.ReflectionTestUtils
import java.util.*
import java.util.function.Consumer

@ExtendWith(MockitoExtension::class)
internal class PlanUnmuteSequenceTest {

    @Mock
    private lateinit var user: User

    @Mock
    private lateinit var channel: TextChannel

    @Mock
    private lateinit var scheduledUnmuteService: ScheduledUnmuteService

    @Mock
    private lateinit var targetUser: User

    @Mock
    private lateinit var messageAction: MessageAction

    @Mock
    private lateinit var jda: JDA

    @Mock
    private lateinit var messageReceivedEvent: MessageReceivedEvent

    @Mock
    private lateinit var message: Message

    @Mock
    private lateinit var guild: Guild

    @Mock
    private lateinit var guildLogger: GuildLogger

    private lateinit var planUnmuteSequence: PlanUnmuteSequence


    private lateinit var stubs: Array<Any>

    @BeforeEach
    fun `Set action for init`() {
        whenever(channel.sendMessage(any<String>())).thenReturn(messageAction)
        planUnmuteSequence = spy(PlanUnmuteSequence(user, channel, scheduledUnmuteService, targetUser, guildLogger))
        stubs = arrayOf(user, channel, scheduledUnmuteService, targetUser, messageAction, jda, messageReceivedEvent, message, guild, planUnmuteSequence, guildLogger)
    }


    @AfterEach
    fun `No more interactions with any spy or mocks`() {
        verifyNoMoreInteractions(*stubs)
    }

    @Test
    fun `When providing a valid answer to the first question a mute should be planned`() {
        // Arrange
        whenever(messageReceivedEvent.message).thenReturn(message)
        whenever(message.contentRaw).thenReturn("30")
        whenever(channel.guild).thenReturn(guild)
        whenever(targetUser.name).thenReturn("A target user")
        whenever(user.name).thenReturn("A moderator")
        whenever(user.jda).thenReturn(jda)
        // Act
        val methodInvocationName = "onMessageReceivedDuringSequence"
        ReflectionTestUtils.invokeMethod<Void>(
                planUnmuteSequence,
                methodInvocationName,
                messageReceivedEvent
        )
        // Verify
        verify(scheduledUnmuteService).planUnmute(any(), any(), any())
        val onMessageReceivedDuringSequence = mockingDetails(planUnmuteSequence).invocations.filter { it.method.name == methodInvocationName }
        onMessageReceivedDuringSequence.first().markVerified()
        verify(targetUser).idLong
        verify(channel, times(3)).sendMessage(any<String>())
        verify(channel).asMention
        verify(user).asMention
        verify(guild).idLong
        verify(messageAction, times(3)).queue(any<Consumer<Message>>())
        verify(guild).getMember(user)
        verify(guild).getMember(targetUser)
        verify(guildLogger).log(any(), eq(targetUser), eq(guild), eq(null), eq(GuildLogger.LogTypeAction.MODERATOR), eq(null))
        verify(jda).removeEventListener(planUnmuteSequence)
    }

    @Test
    fun `Providing a non-numeric message should fail`() {
        // Arrange
        whenever(messageReceivedEvent.message).thenReturn(message)
        whenever(message.contentRaw).thenReturn("Definitely not a number")
        // Act
        val methodInvocationName = "onMessageReceivedDuringSequence"
        val numberFormatException = assertThrows<NumberFormatException> {
            ReflectionTestUtils.invokeMethod<Void>(
                    planUnmuteSequence, methodInvocationName,
                    messageReceivedEvent)
        }
        // Verify
        assertEquals("For input string: \"Definitely not a number\"", numberFormatException.message)
        val onMessageReceivedDuringSequence = mockingDetails(planUnmuteSequence).invocations.filter { it.method.name == methodInvocationName }
        onMessageReceivedDuringSequence.first().markVerified()
        verify(channel, times(2)).sendMessage(any<String>())
        verify(channel).asMention
        verify(user).asMention
        verify(messageAction, times(2)).queue(any())
    }

    @Test
    fun `Providing a negative number fails`() {
        // Arrange
        whenever(messageReceivedEvent.message).thenReturn(message)
        whenever(message.contentRaw).thenReturn("-123")
        // Act
        val methodInvocationName = "onMessageReceivedDuringSequence"
        val illegalArgumentException = assertThrows<IllegalArgumentException> {
            ReflectionTestUtils.invokeMethod<Void>(
                    planUnmuteSequence, methodInvocationName,
                    messageReceivedEvent)
        }
        // Verify
        assertEquals("The numbers of days should not be negative or 0", illegalArgumentException.message)
        val onMessageReceivedDuringSequence = mockingDetails(planUnmuteSequence).invocations.filter { it.method.name == methodInvocationName }
        onMessageReceivedDuringSequence.first().markVerified()
        verify(channel, times(2)).sendMessage(any<String>())
        verify(channel).asMention
        verify(user).asMention
        verify(messageAction, times(2)).queue(any())
    }
}

@SpringBootTest(classes = [PlanUnmuteCommand::class])
@ExtendWith(MockitoExtension::class)
internal class PlanUnmuteCommandTest {
    @MockBean
    private lateinit var scheduledUnmuteService: ScheduledUnmuteService

    @MockBean
    private lateinit var guildLogger: GuildLogger

    @MockBean
    private lateinit var muteRolesRepository: MuteRolesRepository

    @SpyBean
    private lateinit var planUnmuteCommand: PlanUnmuteCommand

    @Mock
    private lateinit var messageReceivedEvent: MessageReceivedEvent

    @Mock
    private lateinit var jda: JDA

    @Mock
    private lateinit var message: Message

    @Mock
    private lateinit var guild: Guild

    @Mock
    private lateinit var member: Member

    @Mock
    private lateinit var user: User

    @Mock
    private lateinit var messageAction: MessageAction

    @Mock
    private lateinit var messageChannel: MessageChannel

    @AfterEach
    fun `No more interactions with any spy or mocks`() {
        verifyNoMoreInteractions(jda, planUnmuteCommand, scheduledUnmuteService, message, message, guild, member, user,
                messageAction, messageChannel, guildLogger, muteRolesRepository)
    }

    @Test
    fun `Executing a valid command works`() {
        // Arrange
        val messageContent = "!${planUnmuteCommand.aliases[0]} <@1>"
        clearInvocations(planUnmuteCommand)
        val optional = Optional.of(MuteRole(1, 1))
        whenever(muteRolesRepository.findById(any())).thenReturn(optional)
        val roles = mock<List<Role>>()
        whenever(member.roles).thenReturn(roles)
        whenever(roles.contains(any())).thenReturn(true)
        val role = mock<Role>()
        whenever(guild.getRoleById(any<Long>())).thenReturn(role)
        whenever(member.guild).thenReturn(guild)
        whenever(messageReceivedEvent.message).thenReturn(message)
        whenever(message.contentRaw).thenReturn(messageContent)
        whenever(messageReceivedEvent.guild).thenReturn(guild)
        whenever(guild.getMemberById(1)).thenReturn(member)
        whenever(member.user).thenReturn(user)
        whenever(messageReceivedEvent.author).thenReturn(user)
        whenever(messageReceivedEvent.channel).thenReturn(messageChannel)
        whenever(messageChannel.sendMessage(anyString())).thenReturn(messageAction)
        whenever(messageReceivedEvent.jda).thenReturn(jda)
        // Act
        ReflectionTestUtils.invokeMethod<Void>(
                planUnmuteCommand, "commandExec",
                messageReceivedEvent, planUnmuteCommand.aliases[0], null)
        // Assert
        verify(planUnmuteCommand).aliases
        val commandExecInvocations = mockingDetails(planUnmuteCommand).invocations.filter { it.method.name == "commandExec" }
        assertEquals(1, commandExecInvocations.size, "Expected method commandExec to be executed once.")
        commandExecInvocations.first().markVerified()
        verify(muteRolesRepository).findById(any<Long>())
        verify(messageReceivedEvent).message
        verify(messageReceivedEvent).guild
        verify(guild).idLong
        verify(guild).getMemberById(1)
        verify(member).user
        verify(jda).addEventListener(any<PlanUnmuteSequence>())
        verify(user).asMention
        verify(messageAction, times(2)).queue(any())
    }

    @Test
    fun `Executing a command without id does not start sequence`() {
        // Arrange
        val messageContent = "!${planUnmuteCommand.aliases[0]}"
        clearInvocations(planUnmuteCommand)
        whenever(messageReceivedEvent.message).thenReturn(message)
        whenever(message.contentRaw).thenReturn(messageContent)
        // Act
        val illegalArgumentException = assertThrows<IllegalArgumentException> {
            ReflectionTestUtils.invokeMethod<Void>(
                    planUnmuteCommand, "commandExec",
                    messageReceivedEvent, planUnmuteCommand.aliases[0], null)
        }
        // Assert
        assertEquals("This command requires a user id or mention", illegalArgumentException.message)
        verify(planUnmuteCommand).aliases
        val commandExecInvocations = mockingDetails(planUnmuteCommand).invocations.filter { it.method.name == "commandExec" }
        assertEquals(1, commandExecInvocations.size, "Expected method commandExec to be executed once.")
        commandExecInvocations.first().markVerified()
        verify(messageReceivedEvent).message
    }

    @Test
    fun `Executing a command with invalid parameters does not start sequence`() {
        // Arrange
        val messageContent = "!${planUnmuteCommand.aliases[0]} invalid parameters"
        clearInvocations(planUnmuteCommand)
        whenever(messageReceivedEvent.message).thenReturn(message)
        whenever(message.contentRaw).thenReturn(messageContent)
        // Act & Assert throws
        val illegalArgumentException = assertThrows<IllegalArgumentException> {
            ReflectionTestUtils.invokeMethod<Void>(
                    planUnmuteCommand, "commandExec",
                    messageReceivedEvent, planUnmuteCommand.aliases[0], null)
        }
        // Assert
        assertEquals("This command requires a user id or mention", illegalArgumentException.message)
        verify(planUnmuteCommand).aliases
        val commandExecInvocations = mockingDetails(planUnmuteCommand).invocations.filter { it.method.name == "commandExec" }
        assertEquals(1, commandExecInvocations.size, "Expected method commandExec to be executed once.")
        commandExecInvocations.first().markVerified()
        verify(messageReceivedEvent).message
    }
}
