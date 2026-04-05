package be.duncanc.discordmodbot.member.gate

import be.duncanc.discordmodbot.member.gate.persistence.WelcomeMessage
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.Member
import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.entities.Role
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.interactions.InteractionHook
import net.dv8tion.jda.api.interactions.commands.Command
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData
import net.dv8tion.jda.api.requests.restaction.WebhookMessageCreateAction
import net.dv8tion.jda.api.requests.restaction.interactions.AutoCompleteCallbackAction
import net.dv8tion.jda.api.requests.restaction.interactions.ReplyCallbackAction
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Answers
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.*
import java.util.function.Consumer

@ExtendWith(MockitoExtension::class)
class GateConfigCommandTest {
    @Mock
    private lateinit var memberGateService: MemberGateService

    @Mock
    private lateinit var welcomeMessageService: WelcomeMessageService

    @Mock
    private lateinit var slashEvent: SlashCommandInteractionEvent

    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private lateinit var autoCompleteEvent: CommandAutoCompleteInteractionEvent

    @Mock
    private lateinit var guild: Guild

    @Mock
    private lateinit var jda: JDA

    @Mock
    private lateinit var member: Member

    @Mock
    private lateinit var role: Role

    @Mock
    private lateinit var replyAction: ReplyCallbackAction

    @Mock
    private lateinit var interactionHook: InteractionHook

    @Mock
    private lateinit var followupAction: WebhookMessageCreateAction<Message>

    @Mock
    private lateinit var autoCompleteAction: AutoCompleteCallbackAction

    private lateinit var command: GateConfigCommand

    @BeforeEach
    fun setUp() {
        command = GateConfigCommand(memberGateService, welcomeMessageService)
    }

    @Test
    fun `command name filter ignores other slash commands`() {
        whenever(slashEvent.name).thenReturn("other")

        command.onSlashCommandInteraction(slashEvent)

        verify(slashEvent, never()).reply(any<String>())
    }

    @Test
    fun `missing member returns guild error`() {
        stubReply()
        whenever(slashEvent.name).thenReturn("gateconfig")
        whenever(slashEvent.guild).thenReturn(guild)
        whenever(slashEvent.member).thenReturn(null)

        command.onSlashCommandInteraction(slashEvent)

        verify(slashEvent).reply("This command only works in a guild.")
    }

    @Test
    fun `missing manage roles permission returns error`() {
        stubReply()
        whenever(slashEvent.name).thenReturn("gateconfig")
        whenever(slashEvent.guild).thenReturn(guild)
        whenever(slashEvent.member).thenReturn(member)
        whenever(member.hasPermission(Permission.MANAGE_ROLES)).thenReturn(false)

        command.onSlashCommandInteraction(slashEvent)

        verify(slashEvent).reply("You need manage roles permission to use this command.")
    }

    @Test
    fun `question autocomplete filters matching questions`() {
        whenever(autoCompleteEvent.replyChoices(any<Collection<Command.Choice>>())).thenReturn(autoCompleteAction)
        stubQuestionAutocomplete(query = "app")
        whenever(memberGateService.getQuestions(1L)).thenReturn(setOf("banana", "apple", "application"))

        command.onCommandAutoCompleteInteraction(autoCompleteEvent)

        val choicesCaptor = argumentCaptor<Collection<Command.Choice>>()
        verify(autoCompleteEvent).replyChoices(choicesCaptor.capture())
        val choices = choicesCaptor.firstValue.toList()

        assertEquals(2, choices.size)
        assertEquals(listOf("apple", "application"), choices.map { it.name })
        assertEquals(listOf("apple", "application"), choices.map { it.asString })
    }

    @Test
    fun `welcome autocomplete uses ids as values`() {
        whenever(autoCompleteEvent.replyChoices(any<Collection<Command.Choice>>())).thenReturn(autoCompleteAction)
        stubWelcomeAutocomplete(query = "hello")
        whenever(welcomeMessageService.getWelcomeMessages(1L)).thenReturn(
            listOf(
                WelcomeMessage(id = 11L, guildId = 1L, imageUrl = "https://example.com/1.png", message = "hello there"),
                WelcomeMessage(id = 12L, guildId = 1L, imageUrl = "https://example.com/2.png", message = "goodbye")
            )
        )

        command.onCommandAutoCompleteInteraction(autoCompleteEvent)

        val choicesCaptor = argumentCaptor<Collection<Command.Choice>>()
        verify(autoCompleteEvent).replyChoices(choicesCaptor.capture())
        val choice = choicesCaptor.firstValue.single()

        assertEquals("11", choice.asString)
        assertTrue(choice.name.contains("#11:"))
        assertTrue(choice.name.contains("hello there"))
    }

    @Test
    fun `remove question rejects values outside autocomplete suggestions`() {
        stubAuthorizedSlashContext(subcommandName = "remove-question", includeReply = true)
        val questionOption = stringOption("missing")
        whenever(slashEvent.getOption("question")).thenReturn(questionOption)
        whenever(memberGateService.getQuestions(1L)).thenReturn(setOf("expected"))

        command.onSlashCommandInteraction(slashEvent)

        verify(slashEvent).reply("That question was not found. Please choose one of the suggested questions.")
    }

    @Test
    fun `set member role stores selected role`() {
        stubAuthorizedSlashContext(subcommandName = "set-member-role", includeReply = true)
        val roleOption = roleOption(role)
        whenever(slashEvent.getOption("role")).thenReturn(roleOption)
        whenever(role.asMention).thenReturn("<@&5>")

        command.onSlashCommandInteraction(slashEvent)

        verify(memberGateService).setMemberRole(1L, role)
        verify(slashEvent).reply("Member role set to <@&5>.")
    }

    @Test
    fun `show lists all configured questions and welcome messages`() {
        stubAuthorizedSlashContext(subcommandName = "show")
        stubDeferredReply()
        whenever(guild.jda).thenReturn(jda)
        whenever(guild.name).thenReturn("Test Guild")
        whenever(memberGateService.getQuestions(1L)).thenReturn(setOf("First question?", "Second question?"))
        whenever(welcomeMessageService.getWelcomeMessages(1L)).thenReturn(
            listOf(
                WelcomeMessage(id = 11L, guildId = 1L, imageUrl = "https://example.com/1.png", message = "hello there"),
                WelcomeMessage(
                    id = 12L,
                    guildId = 1L,
                    imageUrl = "https://example.com/2.png",
                    message = "welcome aboard"
                )
            )
        )

        command.onSlashCommandInteraction(slashEvent)

        val replyCaptor = argumentCaptor<String>()
        verify(interactionHook, atLeastOnce()).sendMessage(replyCaptor.capture())

        val content = replyCaptor.allValues.joinToString("\n")
        assertTrue(content.contains("Questions configured: 2"))
        assertTrue(content.contains("1. First question?"))
        assertTrue(content.contains("2. Second question?"))
        assertTrue(content.contains("Welcome messages configured: 2"))
        assertTrue(content.contains("- #11"))
        assertTrue(content.contains("hello there"))
        assertTrue(content.contains("- #12"))
        assertTrue(content.contains("welcome aboard"))
    }

    @Test
    fun `show splits oversized output into followup messages`() {
        stubAuthorizedSlashContext(subcommandName = "show")
        stubDeferredReply(includeEphemeralFollowup = true)
        whenever(guild.jda).thenReturn(jda)
        whenever(guild.name).thenReturn("Test Guild")
        whenever(memberGateService.getQuestions(1L)).thenReturn(
            (1..60).map { "Question $it ${"x".repeat(50)}" }.toSet()
        )
        whenever(welcomeMessageService.getWelcomeMessages(1L)).thenReturn(emptyList())

        command.onSlashCommandInteraction(slashEvent)

        val followupCaptor = argumentCaptor<String>()
        verify(interactionHook, atLeastOnce()).sendMessage(followupCaptor.capture())

        assertTrue(followupCaptor.allValues.all { it.length <= Message.MAX_CONTENT_LENGTH })
    }

    @Test
    fun `add question command limits question length to persistence maximum`() {
        val option = getSubcommand("add-question").options.single { it.name == "question" }

        assertEquals(100, option.maxLength)
    }

    @Test
    fun `add welcome command limits message length to persistence maximum`() {
        val option = getSubcommand("add-welcome").options.single { it.name == "message" }

        assertEquals(2048, option.maxLength)
    }

    private fun stubSlashContext(subcommandName: String, includeReply: Boolean = false) {
        if (includeReply) {
            stubReply()
        }
        whenever(slashEvent.name).thenReturn("gateconfig")
        whenever(slashEvent.guild).thenReturn(guild)
        whenever(slashEvent.member).thenReturn(member)
        whenever(slashEvent.subcommandName).thenReturn(subcommandName)
        whenever(guild.idLong).thenReturn(1L)
    }

    private fun stubAuthorizedSlashContext(subcommandName: String, includeReply: Boolean = false) {
        stubSlashContext(subcommandName, includeReply)
        whenever(member.hasPermission(Permission.MANAGE_ROLES)).thenReturn(true)
    }

    private fun stubQuestionAutocomplete(query: String) {
        whenever(autoCompleteEvent.name).thenReturn("gateconfig")
        whenever(autoCompleteEvent.guild).thenReturn(guild)
        whenever(guild.idLong).thenReturn(1L)
        whenever(autoCompleteEvent.subcommandName).thenReturn("remove-question")
        whenever(autoCompleteEvent.focusedOption.name).thenReturn("question")
        whenever(autoCompleteEvent.focusedOption.value).thenReturn(query)
    }

    private fun stubWelcomeAutocomplete(query: String) {
        whenever(autoCompleteEvent.name).thenReturn("gateconfig")
        whenever(autoCompleteEvent.guild).thenReturn(guild)
        whenever(guild.idLong).thenReturn(1L)
        whenever(autoCompleteEvent.subcommandName).thenReturn("remove-welcome")
        whenever(autoCompleteEvent.focusedOption.name).thenReturn("welcome")
        whenever(autoCompleteEvent.focusedOption.value).thenReturn(query)
    }

    private fun stubReply() {
        whenever(slashEvent.reply(any<String>())).thenReturn(replyAction)
        whenever(replyAction.setEphemeral(true)).thenReturn(replyAction)
    }

    @Suppress("UNCHECKED_CAST")
    private fun stubDeferredReply(includeEphemeralFollowup: Boolean = false) {
        whenever(slashEvent.deferReply(true)).thenReturn(replyAction)
        whenever(interactionHook.sendMessage(any<String>())).thenReturn(followupAction)
        if (includeEphemeralFollowup) {
            whenever(followupAction.setEphemeral(true)).thenReturn(followupAction)
        }
        doAnswer {
            val consumer = it.arguments[0] as Consumer<InteractionHook>
            consumer.accept(interactionHook)
            null
        }.whenever(replyAction).queue(any<Consumer<InteractionHook>>())
    }

    private fun getSubcommand(name: String): SubcommandData {
        return command.getCommandsData().single().subcommands.single { it.name == name }
    }

    private fun stringOption(value: String) = mock<net.dv8tion.jda.api.interactions.commands.OptionMapping> {
        on { asString } doReturn value
    }

    private fun roleOption(role: Role) = mock<net.dv8tion.jda.api.interactions.commands.OptionMapping> {
        on { asRole } doReturn role
    }
}
