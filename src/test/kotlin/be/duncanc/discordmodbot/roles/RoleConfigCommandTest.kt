package be.duncanc.discordmodbot.roles

import be.duncanc.discordmodbot.roles.persistence.IAmRolesCategory
import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.Member
import net.dv8tion.jda.api.entities.Role
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.interactions.InteractionContextType
import net.dv8tion.jda.api.interactions.commands.Command
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData
import net.dv8tion.jda.api.requests.restaction.interactions.AutoCompleteCallbackAction
import net.dv8tion.jda.api.requests.restaction.interactions.ReplyCallbackAction
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Answers
import org.mockito.Mock
import org.mockito.Mockito.lenient
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@ExtendWith(MockitoExtension::class)
class RoleConfigCommandTest {
    @Mock
    private lateinit var iAmRolesService: IAmRolesService

    @Mock
    private lateinit var slashEvent: SlashCommandInteractionEvent

    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private lateinit var autoCompleteEvent: CommandAutoCompleteInteractionEvent

    @Mock
    private lateinit var guild: Guild

    @Mock
    private lateinit var member: Member

    @Mock
    private lateinit var role: Role

    @Mock
    private lateinit var replyAction: ReplyCallbackAction

    @Mock
    private lateinit var autoCompleteAction: AutoCompleteCallbackAction

    private lateinit var command: TestRoleConfigCommand

    @BeforeEach
    fun setUp() {
        command = TestRoleConfigCommand(iAmRolesService)

        lenient().whenever(slashEvent.reply(any<String>())).thenReturn(replyAction)
        lenient().whenever(replyAction.setEphemeral(true)).thenReturn(replyAction)
        lenient().whenever(slashEvent.name).thenReturn("roleconfig")
        lenient().whenever(slashEvent.guild).thenReturn(guild)
        lenient().whenever(slashEvent.member).thenReturn(member)
        lenient().whenever(guild.idLong).thenReturn(1L)
        lenient().whenever(member.hasPermission(Permission.MANAGE_ROLES)).thenReturn(true)
        lenient().whenever(autoCompleteEvent.replyChoices(any<Collection<Command.Choice>>()))
            .thenReturn(autoCompleteAction)
    }

    @Test
    fun `missing manage roles permission returns error`() {
        whenever(member.hasPermission(Permission.MANAGE_ROLES)).thenReturn(false)

        command.onSlashCommandInteraction(slashEvent)

        verify(slashEvent).reply("You need manage roles permission to use this command.")
    }

    @Test
    fun `add category stores requested values`() {
        whenever(slashEvent.subcommandName).thenReturn("add-category")
        command.name = "Games"
        command.limit = 0

        command.onSlashCommandInteraction(slashEvent)

        verify(iAmRolesService).addNewCategory(1L, "Games", 0)
        verify(slashEvent).reply("Category Games added with limit unlimited roles.")
    }

    @Test
    fun `duplicate role rejection is shown to the user`() {
        whenever(slashEvent.subcommandName).thenReturn("add-role")
        command.categoryId = 5L
        command.role = role
        whenever(role.idLong).thenReturn(10L)
        whenever(iAmRolesService.getCategory(1L, 5L)).thenReturn(IAmRolesCategory(1L, 5L, "Games", 0))
        whenever(iAmRolesService.addRoleToCategory(1L, 5L, 10L)).thenThrow(
            IllegalArgumentException("That role is already assigned to category Other.")
        )

        command.onSlashCommandInteraction(slashEvent)

        verify(slashEvent).reply("That role is already assigned to category Other.")
    }

    @Test
    fun `category autocomplete returns ids as values`() {
        whenever(autoCompleteEvent.name).thenReturn("roleconfig")
        whenever(autoCompleteEvent.guild).thenReturn(guild)
        whenever(autoCompleteEvent.focusedOption.name).thenReturn("category")
        whenever(autoCompleteEvent.focusedOption.value).thenReturn("ga")
        whenever(guild.idLong).thenReturn(1L)
        whenever(iAmRolesService.getSortedCategoriesForGuild(1L)).thenReturn(
            listOf(
                IAmRolesCategory(1L, 5L, "Games", 0),
                IAmRolesCategory(1L, 6L, "General", 1)
            )
        )

        command.onCommandAutoCompleteInteraction(autoCompleteEvent)

        val choicesCaptor = argumentCaptor<Collection<Command.Choice>>()
        verify(autoCompleteEvent).replyChoices(choicesCaptor.capture())
        val choice = choicesCaptor.firstValue.single()
        assertEquals("Games", choice.name)
        assertEquals("5", choice.asString)
    }

    @Test
    fun `command data exposes expected admin metadata`() {
        val commandData = command.getCommandsData().single()

        assertEquals("roleconfig", commandData.name)
        assertEquals(setOf(InteractionContextType.GUILD), commandData.contexts)
        assertEquals(
            listOf(
                "show",
                "add-category",
                "remove-category",
                "rename-category",
                "set-limit",
                "add-role",
                "remove-role"
            ),
            commandData.subcommands.map(SubcommandData::getName)
        )
    }

    private class TestRoleConfigCommand(
        iAmRolesService: IAmRolesService
    ) : RoleConfigCommand(iAmRolesService) {
        var categoryId: Long? = null
        var name: String? = null
        var limit: Int? = null
        var role: Role? = null

        override fun getCategoryId(event: SlashCommandInteractionEvent): Long? {
            return categoryId
        }

        override fun getNameOption(event: SlashCommandInteractionEvent): String? {
            return name
        }

        override fun getLimitOption(event: SlashCommandInteractionEvent): Int? {
            return limit
        }

        override fun getRoleOption(event: SlashCommandInteractionEvent): Role? {
            return role
        }
    }
}
