package be.duncanc.discordmodbot.roles

import be.duncanc.discordmodbot.roles.persistence.IAmRolesCategory
import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.components.actionrow.ActionRow
import net.dv8tion.jda.api.entities.*
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent
import net.dv8tion.jda.api.interactions.InteractionContextType
import net.dv8tion.jda.api.interactions.InteractionHook
import net.dv8tion.jda.api.interactions.commands.Command
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData
import net.dv8tion.jda.api.requests.restaction.AuditableRestAction
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
    private lateinit var deferredReplyAction: ReplyCallbackAction

    @Mock
    private lateinit var autoCompleteAction: AutoCompleteCallbackAction

    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private lateinit var interactionHook: InteractionHook

    @Mock
    private lateinit var roleUpdateAction: AuditableRestAction<Void>

    @Mock
    private lateinit var assignedRole: Role

    @Mock
    private lateinit var availableRole: Role

    private lateinit var command: TestRolesCommand

    @BeforeEach
    @Suppress("UNCHECKED_CAST")
    fun setUp() {
        command = TestRolesCommand(iAmRolesService)
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
        stubSlashReply(includeComponents = true)
        stubManageableSelfMember()
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
        stubSlashReply()
        stubManageableSelfMember()
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
        stubSlashReply()
        stubManageableSelfMember()
        whenever(member.roles).thenReturn(emptyList())

        command.onSlashCommandInteraction(slashEvent)

        verify(slashEvent).reply("You have no roles to remove from this category.")
    }

    @Test
    fun `slash command replies when selected category no longer exists`() {
        stubSlashReply()
        stubManageableSelfMember()
        whenever(slashEvent.name).thenReturn("role")
        whenever(slashEvent.guild).thenReturn(guild)
        whenever(slashEvent.member).thenReturn(member)
        whenever(guild.idLong).thenReturn(1L)
        whenever(slashEvent.subcommandName).thenReturn("assign")
        command.categoryId = 5L
        whenever(iAmRolesService.getCategory(1L, 5L)).thenThrow(IllegalArgumentException("missing"))

        command.onSlashCommandInteraction(slashEvent)

        verify(slashEvent).reply("This role menu is out of date. Run the command again.")
    }

    @Test
    fun `category autocomplete returns category ids as values`() {
        whenever(autoCompleteEvent.replyChoices(any<Collection<Command.Choice>>())).thenReturn(autoCompleteAction)
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
        stubSelectInteractionContext()
        stubSelectReply()
        whenever(selectEvent.componentId).thenReturn("role:menu:assign:99:5:0")
        whenever(user.idLong).thenReturn(100L)

        command.onStringSelectInteraction(selectEvent)

        verify(selectEvent).reply("This role menu belongs to someone else. Run the command yourself.")
    }

    @Test
    @Suppress("UNCHECKED_CAST")
    fun `select interaction defers reply before assigning roles`() {
        val category = IAmRolesCategory(1L, 5L, "Games", 0, mutableSetOf(11L))
        stubSelectContext("assign", category, listOf("11"))
        stubDeferredSelectReply()
        stubManageableSelfMember()
        whenever(roleUpdateAction.reason(any())).thenReturn(roleUpdateAction)
        whenever(member.roles).thenReturn(emptyList())
        whenever(availableRole.id).thenReturn("11")
        whenever(selfMember.canInteract(availableRole)).thenReturn(true)
        whenever(guild.getRoleById(11L)).thenReturn(availableRole)
        whenever(guild.modifyMemberRoles(member, listOf(availableRole), null)).thenReturn(roleUpdateAction)
        doAnswer {
            val consumer = it.arguments[0] as Consumer<Void?>
            consumer.accept(null)
            null
        }.whenever(roleUpdateAction).queue(any<Consumer<Void?>>(), any<Consumer<Throwable>>())

        command.onStringSelectInteraction(selectEvent)

        verify(selectEvent).deferReply(true)
        verify(interactionHook).editOriginal("Your requested role(s) were added.")
    }

    @Test
    fun `select interaction defers before stale category check`() {
        stubSelectInteractionContext()
        stubDeferredSelectReply()
        stubManageableSelfMember()
        whenever(guild.idLong).thenReturn(1L)
        whenever(selectEvent.componentId).thenReturn("role:menu:assign:99:5:0")
        whenever(iAmRolesService.getCategory(1L, 5L)).thenThrow(IllegalArgumentException("missing"))

        command.onStringSelectInteraction(selectEvent)

        verify(selectEvent).deferReply(true)
        verify(interactionHook).editOriginal("This role menu is out of date. Run the command again.")
        verify(selectEvent, never()).reply(any<String>())
    }

    @Test
    @Suppress("UNCHECKED_CAST")
    fun `select interaction reports async role update failures`() {
        val category = IAmRolesCategory(1L, 5L, "Games", 0, mutableSetOf(11L))
        stubSelectContext("assign", category, listOf("11"))
        stubDeferredSelectReply()
        stubManageableSelfMember()
        whenever(roleUpdateAction.reason(any())).thenReturn(roleUpdateAction)
        whenever(member.roles).thenReturn(emptyList())
        whenever(availableRole.id).thenReturn("11")
        whenever(selfMember.canInteract(availableRole)).thenReturn(true)
        whenever(guild.getRoleById(11L)).thenReturn(availableRole)
        whenever(guild.modifyMemberRoles(member, listOf(availableRole), null)).thenReturn(roleUpdateAction)
        doAnswer {
            val consumer = it.arguments[1] as Consumer<Throwable>
            consumer.accept(IllegalStateException("Discord rejected the update"))
            null
        }.whenever(roleUpdateAction).queue(any<Consumer<Void?>>(), any<Consumer<Throwable>>())

        command.onStringSelectInteraction(selectEvent)

        verify(selectEvent).deferReply(true)
        verify(interactionHook).editOriginal("I couldn't update your roles right now. Please try again.")
    }

    @Test
    fun `command data exposes assign and remove subcommands`() {
        val commandData = command.getCommandsData().single()

        assertEquals("role", commandData.name)
        assertEquals(setOf(InteractionContextType.GUILD), commandData.contexts)
        assertEquals(listOf("assign", "remove"), commandData.subcommands.map(SubcommandData::getName))
    }

    private fun stubSlashContext(subcommandName: String, category: IAmRolesCategory) {
        whenever(slashEvent.name).thenReturn("role")
        whenever(slashEvent.guild).thenReturn(guild)
        whenever(slashEvent.member).thenReturn(member)
        whenever(guild.idLong).thenReturn(1L)
        whenever(slashEvent.subcommandName).thenReturn(subcommandName)
        command.categoryId = category.categoryId
        whenever(iAmRolesService.getCategory(1L, category.categoryId!!)).thenReturn(category)
    }

    private fun stubSelectContext(subcommandName: String, category: IAmRolesCategory, values: List<String>) {
        stubSelectInteractionContext()
        whenever(guild.idLong).thenReturn(1L)
        whenever(selectEvent.componentId).thenReturn("role:menu:$subcommandName:99:${category.categoryId}:0")
        whenever(selectEvent.values).thenReturn(values)
        whenever(iAmRolesService.getCategory(1L, category.categoryId!!)).thenReturn(category)
    }

    private fun stubSlashReply(includeComponents: Boolean = false) {
        whenever(slashEvent.reply(any<String>())).thenReturn(replyAction)
        whenever(replyAction.setEphemeral(true)).thenReturn(replyAction)
        if (includeComponents) {
            whenever(replyAction.addComponents(any<ActionRow>())).thenReturn(replyAction)
        }
    }

    private fun stubSelectReply() {
        whenever(selectEvent.reply(any<String>())).thenReturn(replyAction)
        whenever(replyAction.setEphemeral(true)).thenReturn(replyAction)
    }

    @Suppress("UNCHECKED_CAST")
    private fun stubDeferredSelectReply() {
        whenever(selectEvent.deferReply(true)).thenReturn(deferredReplyAction)
        doAnswer {
            val consumer = it.arguments[0] as Consumer<InteractionHook>
            consumer.accept(interactionHook)
            null
        }.whenever(deferredReplyAction).queue(any<Consumer<InteractionHook>>())
    }

    private fun stubSelectInteractionContext() {
        whenever(selectEvent.guild).thenReturn(guild)
        whenever(selectEvent.member).thenReturn(member)
        whenever(selectEvent.user).thenReturn(user)
        whenever(user.idLong).thenReturn(99L)
    }

    private fun stubManageableSelfMember() {
        whenever(guild.selfMember).thenReturn(selfMember)
        whenever(selfMember.hasPermission(Permission.MANAGE_ROLES)).thenReturn(true)
        whenever(selfMember.canInteract(member)).thenReturn(true)
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
