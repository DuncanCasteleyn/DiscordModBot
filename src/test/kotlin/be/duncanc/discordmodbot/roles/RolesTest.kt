package be.duncanc.discordmodbot.roles

import be.duncanc.discordmodbot.roles.persistence.IAmRolesCategory
import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.components.actionrow.ActionRow
import net.dv8tion.jda.api.entities.*
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent
import net.dv8tion.jda.api.interactions.InteractionContextType
import net.dv8tion.jda.api.interactions.commands.Command
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData
import net.dv8tion.jda.api.requests.restaction.interactions.AutoCompleteCallbackAction
import net.dv8tion.jda.api.requests.restaction.interactions.ReplyCallbackAction
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Answers
import org.mockito.Mock
import org.mockito.Mockito.lenient
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.*

@ExtendWith(MockitoExtension::class)
class RolesTest {
    @Mock
    private lateinit var iAmRolesService: IAmRolesService

    @Mock
    private lateinit var slashEvent: SlashCommandInteractionEvent

    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private lateinit var autoCompleteEvent: CommandAutoCompleteInteractionEvent

    @Mock
    private lateinit var selectEvent: StringSelectInteractionEvent

    @Mock
    private lateinit var guild: Guild

    @Mock
    private lateinit var member: Member

    @Mock
    private lateinit var selfMember: SelfMember

    @Mock
    private lateinit var user: User

    @Mock
    private lateinit var replyAction: ReplyCallbackAction

    @Mock
    private lateinit var autoCompleteAction: AutoCompleteCallbackAction

    @Mock
    private lateinit var assignedRole: Role

    @Mock
    private lateinit var availableRole: Role

    private lateinit var command: TestRolesCommand

    @BeforeEach
    fun setUp() {
        command = TestRolesCommand(iAmRolesService)

        lenient().whenever(slashEvent.reply(any<String>())).thenReturn(replyAction)
        lenient().whenever(selectEvent.reply(any<String>())).thenReturn(replyAction)
        lenient().whenever(replyAction.setEphemeral(true)).thenReturn(replyAction)
        lenient().whenever(replyAction.addComponents(any<ActionRow>())).thenReturn(replyAction)
        lenient().whenever(autoCompleteEvent.replyChoices(any<Collection<Command.Choice>>()))
            .thenReturn(autoCompleteAction)

        lenient().whenever(slashEvent.name).thenReturn("role")
        lenient().whenever(slashEvent.guild).thenReturn(guild)
        lenient().whenever(slashEvent.member).thenReturn(member)
        lenient().whenever(guild.idLong).thenReturn(1L)
        lenient().whenever(guild.selfMember).thenReturn(selfMember)
        lenient().whenever(selfMember.hasPermission(Permission.MANAGE_ROLES)).thenReturn(true)
        lenient().whenever(selfMember.canInteract(member)).thenReturn(true)
    }

    @Test
    fun `assign command ignores non matching slash names`() {
        whenever(slashEvent.name).thenReturn("other")

        command.onSlashCommandInteraction(slashEvent)

        verify(slashEvent, never()).reply(any<String>())
    }

    @Test
    fun `assign command supports unlimited categories`() {
        val category = IAmRolesCategory(
            guildId = 1L,
            categoryId = 5L,
            categoryName = "Games",
            allowedRoles = 0,
            roles = mutableSetOf(10L, 11L)
        )
        stubSlashContext("assign", category)
        whenever(member.roles).thenReturn(listOf(assignedRole))
        whenever(assignedRole.idLong).thenReturn(10L)
        whenever(availableRole.idLong).thenReturn(11L)
        whenever(availableRole.id).thenReturn("11")
        whenever(assignedRole.name).thenReturn("Among Us")
        whenever(availableRole.name).thenReturn("Minecraft")
        whenever(guild.getRoleById(10L)).thenReturn(assignedRole)
        whenever(guild.getRoleById(11L)).thenReturn(availableRole)

        command.onSlashCommandInteraction(slashEvent)

        val replyCaptor = argumentCaptor<String>()
        verify(slashEvent).reply(replyCaptor.capture())
        assertTrue(replyCaptor.firstValue.contains("Select the role(s) you'd like to add from Games."))
    }

    @Test
    fun `assign command rejects user already at category limit`() {
        val category = IAmRolesCategory(
            guildId = 1L,
            categoryId = 5L,
            categoryName = "Games",
            allowedRoles = 1,
            roles = mutableSetOf(10L, 11L)
        )
        stubSlashContext("assign", category)
        whenever(member.roles).thenReturn(listOf(assignedRole))
        whenever(assignedRole.idLong).thenReturn(10L)
        whenever(assignedRole.name).thenReturn("Among Us")
        whenever(availableRole.name).thenReturn("Minecraft")
        whenever(guild.getRoleById(10L)).thenReturn(assignedRole)
        whenever(guild.getRoleById(11L)).thenReturn(availableRole)

        command.onSlashCommandInteraction(slashEvent)

        verify(slashEvent).reply("You already have the max amount of roles you can assign for this category.")
    }

    @Test
    fun `remove command rejects when member has no matching roles`() {
        val category = IAmRolesCategory(
            guildId = 1L,
            categoryId = 5L,
            categoryName = "Games",
            allowedRoles = 0,
            roles = mutableSetOf(10L)
        )
        stubSlashContext("remove", category)
        whenever(member.roles).thenReturn(emptyList())

        command.onSlashCommandInteraction(slashEvent)

        verify(slashEvent).reply("You have no roles to remove from this category.")
    }

    @Test
    fun `category autocomplete returns category ids as values`() {
        whenever(autoCompleteEvent.name).thenReturn("role")
        whenever(autoCompleteEvent.guild).thenReturn(guild)
        whenever(autoCompleteEvent.focusedOption.name).thenReturn("category")
        whenever(autoCompleteEvent.focusedOption.value).thenReturn("ga")
        whenever(guild.idLong).thenReturn(1L)
        whenever(iAmRolesService.getSortedCategoriesForGuild(1L)).thenReturn(
            listOf(
                IAmRolesCategory(1L, 5L, "Games", 0),
                IAmRolesCategory(1L, 6L, "General", 0)
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
    fun `select interaction rejects clicks from another user`() {
        whenever(selectEvent.componentId).thenReturn("role:menu:assign:99:5:0")
        whenever(selectEvent.guild).thenReturn(guild)
        whenever(selectEvent.member).thenReturn(member)
        whenever(selectEvent.user).thenReturn(user)
        whenever(user.idLong).thenReturn(100L)

        command.onStringSelectInteraction(selectEvent)

        verify(selectEvent).reply("This role menu belongs to someone else. Run the command yourself.")
    }

    @Test
    fun `command data exposes assign and remove subcommands`() {
        val commandData = command.getCommandsData().single()

        assertEquals("role", commandData.name)
        assertEquals(setOf(InteractionContextType.GUILD), commandData.contexts)
        assertEquals(listOf("assign", "remove"), commandData.subcommands.map(SubcommandData::getName))
    }

    private fun stubSlashContext(subcommandName: String, category: IAmRolesCategory) {
        whenever(slashEvent.subcommandName).thenReturn(subcommandName)
        command.categoryId = category.categoryId
        whenever(iAmRolesService.getCategory(1L, category.categoryId!!)).thenReturn(category)
    }

    private class TestRolesCommand(
        iAmRolesService: IAmRolesService
    ) : Roles(iAmRolesService) {
        var categoryId: Long? = null

        override fun getCategoryId(event: SlashCommandInteractionEvent): Long? {
            return categoryId
        }
    }
}
