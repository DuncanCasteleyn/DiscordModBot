package be.duncanc.discordmodbot.moderation

import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.Member
import net.dv8tion.jda.api.entities.Role
import net.dv8tion.jda.api.entities.SelfMember
import net.dv8tion.jda.api.entities.User
import net.dv8tion.jda.api.events.guild.GuildLeaveEvent
import net.dv8tion.jda.api.events.guild.member.GuildMemberRemoveEvent
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.interactions.InteractionContextType
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData
import net.dv8tion.jda.api.requests.restaction.interactions.ReplyCallbackAction
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@ExtendWith(MockitoExtension::class)
class StickyRolesCommandTest {
    @Mock
    private lateinit var stickyRoleService: StickyRoleService

    @Mock
    private lateinit var slashEvent: SlashCommandInteractionEvent

    @Mock
    private lateinit var guildLeaveEvent: GuildLeaveEvent

    @Mock
    private lateinit var guildMemberRemoveEvent: GuildMemberRemoveEvent

    @Mock
    private lateinit var guild: Guild

    @Mock
    private lateinit var member: Member

    @Mock
    private lateinit var selfMember: SelfMember

    @Mock
    private lateinit var role: Role

    @Mock
    private lateinit var user: User

    @Mock
    private lateinit var replyAction: ReplyCallbackAction

    private lateinit var command: TestStickyRolesCommand

    @BeforeEach
    fun setUp() {
        command = TestStickyRolesCommand(stickyRoleService)
    }

    @Test
    fun `non matching command returns early`() {
        whenever(slashEvent.name).thenReturn("othercommand")

        command.onSlashCommandInteraction(slashEvent)

        verify(slashEvent, never()).reply(any<String>())
    }

    @Test
    fun `missing manage roles permission returns error`() {
        stubSlashCommandContext()
        whenever(member.hasPermission(Permission.MANAGE_ROLES)).thenReturn(false)

        command.onSlashCommandInteraction(slashEvent)

        verify(slashEvent).reply("You need manage roles permission to use this command.")
    }

    @Test
    fun `show displays configured sticky roles`() {
        stubAuthorizedSlashCommand("show")
        whenever(guild.name).thenReturn("Test Guild")
        whenever(stickyRoleService.getConfiguredRoleIds(1L)).thenReturn(setOf(11L, 12L))
        whenever(guild.getRoleById(11L)).thenReturn(role)
        whenever(guild.getRoleById(12L)).thenReturn(null)
        whenever(role.asMention).thenReturn("<@&11>")

        command.onSlashCommandInteraction(slashEvent)

        val replyCaptor = argumentCaptor<String>()
        verify(slashEvent).reply(replyCaptor.capture())
        assertTrue(replyCaptor.firstValue.contains("Sticky role settings for Test Guild"))
        assertTrue(replyCaptor.firstValue.contains("<@&11>"))
        assertTrue(replyCaptor.firstValue.contains("Missing role (ID: 12)"))
    }

    @Test
    fun `add stores sticky role`() {
        stubAuthorizedSlashCommand("add")
        whenever(guild.selfMember).thenReturn(selfMember)
        whenever(selfMember.hasPermission(Permission.MANAGE_ROLES)).thenReturn(true)
        whenever(selfMember.canInteract(role)).thenReturn(true)
        whenever(role.guild).thenReturn(guild)
        whenever(role.idLong).thenReturn(11L)
        whenever(role.asMention).thenReturn("<@&11>")
        whenever(role.isPublicRole).thenReturn(false)
        whenever(role.isManaged).thenReturn(false)
        command.selectedRole = role

        command.onSlashCommandInteraction(slashEvent)

        verify(stickyRoleService).addConfiguredRole(1L, 11L)
        verify(slashEvent).reply("Added <@&11> to sticky roles.")
    }

    @Test
    fun `managed role cannot be added`() {
        stubSlashCommandContext()
        whenever(member.hasPermission(Permission.MANAGE_ROLES)).thenReturn(true)
        whenever(slashEvent.subcommandName).thenReturn("add")
        whenever(role.isManaged).thenReturn(true)
        whenever(role.isPublicRole).thenReturn(false)
        command.selectedRole = role

        command.onSlashCommandInteraction(slashEvent)

        verify(slashEvent).reply("Managed roles cannot be made sticky.")
    }

    @Test
    fun `remove deletes sticky role`() {
        stubAuthorizedSlashCommand("remove")
        whenever(role.idLong).thenReturn(11L)
        whenever(role.asMention).thenReturn("<@&11>")
        command.selectedRole = role

        command.onSlashCommandInteraction(slashEvent)

        verify(stickyRoleService).removeConfiguredRole(1L, 11L)
        verify(slashEvent).reply("Removed <@&11> from sticky roles.")
    }

    @Test
    fun `clear removes sticky role state`() {
        stubAuthorizedSlashCommand("clear")

        command.onSlashCommandInteraction(slashEvent)

        verify(stickyRoleService).clearConfiguredRoles(1L)
        verify(slashEvent).reply("Sticky roles cleared.")
    }

    @Test
    fun `guild member remove stores member role ids`() {
        whenever(guildMemberRemoveEvent.guild).thenReturn(guild)
        whenever(guild.idLong).thenReturn(1L)
        whenever(guildMemberRemoveEvent.user).thenReturn(user)
        whenever(user.idLong).thenReturn(99L)
        whenever(guildMemberRemoveEvent.member).thenReturn(member)
        whenever(member.roles).thenReturn(listOf(role))
        whenever(role.idLong).thenReturn(11L)

        command.onGuildMemberRemove(guildMemberRemoveEvent)

        verify(stickyRoleService).captureRolesOnLeave(1L, 99L, listOf(11L))
    }

    @Test
    fun `guild leave clears guild state`() {
        whenever(guildLeaveEvent.guild).thenReturn(guild)
        whenever(guild.idLong).thenReturn(1L)

        command.onGuildLeave(guildLeaveEvent)

        verify(stickyRoleService).clearGuildState(1L)
    }

    @Test
    fun `command data exposes expected metadata`() {
        val commandData = command.getCommandsData().single()

        assertEquals("stickyroles", commandData.name)
        assertEquals(setOf(InteractionContextType.GUILD), commandData.contexts)
        assertEquals(listOf("show", "add", "remove", "clear"), commandData.subcommands.map(SubcommandData::getName))
    }

    private fun stubSlashCommandContext(member: Member? = this.member) {
        whenever(slashEvent.name).thenReturn("stickyroles")
        whenever(slashEvent.guild).thenReturn(guild)
        whenever(slashEvent.member).thenReturn(member)
        whenever(slashEvent.reply(any<String>())).thenReturn(replyAction)
        whenever(replyAction.setEphemeral(true)).thenReturn(replyAction)
    }

    private fun stubAuthorizedSlashCommand(subcommandName: String) {
        stubSlashCommandContext()
        whenever(guild.idLong).thenReturn(1L)
        whenever(member.hasPermission(Permission.MANAGE_ROLES)).thenReturn(true)
        whenever(slashEvent.subcommandName).thenReturn(subcommandName)
    }

    private class TestStickyRolesCommand(
        stickyRoleService: StickyRoleService
    ) : StickyRolesCommand(stickyRoleService) {
        var selectedRole: Role? = null

        override fun getRequiredRole(event: SlashCommandInteractionEvent): Role? {
            return selectedRole ?: super.getRequiredRole(event)
        }
    }
}
