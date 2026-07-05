package be.duncanc.discordmodbot.moderation

import be.duncanc.discordmodbot.moderation.persistence.ReportSettings
import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.Member
import net.dv8tion.jda.api.entities.Role
import net.dv8tion.jda.api.entities.User
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel
import net.dv8tion.jda.api.entities.channel.unions.GuildChannelUnion
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.interactions.InteractionContextType
import net.dv8tion.jda.api.interactions.commands.OptionMapping
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
class ReportSettingsCommandTest {
    @Mock
    private lateinit var reportSettingsService: ReportSettingsService

    @Mock
    private lateinit var event: SlashCommandInteractionEvent

    @Mock
    private lateinit var guild: Guild

    @Mock
    private lateinit var member: Member

    @Mock
    private lateinit var user: User

    @Mock
    private lateinit var role: Role

    @Mock
    private lateinit var userOption: OptionMapping

    @Mock
    private lateinit var roleOption: OptionMapping

    @Mock
    private lateinit var channelOption: OptionMapping

    @Mock
    private lateinit var channelUnion: GuildChannelUnion

    @Mock
    private lateinit var textChannel: TextChannel

    @Mock
    private lateinit var replyAction: ReplyCallbackAction

    private lateinit var command: ReportSettingsCommand

    @BeforeEach
    fun setUp() {
        command = ReportSettingsCommand(reportSettingsService)
    }

    @Test
    fun `non-matching command name returns early`() {
        whenever(event.name).thenReturn("other")

        command.onSlashCommandInteraction(event)

        verify(event, never()).reply(any<String>())
    }

    @Test
    fun `missing manage roles permission returns error`() {
        stubGuildCommandWithoutSubcommand()
        whenever(member.hasPermission(Permission.MANAGE_ROLES)).thenReturn(false)

        command.onSlashCommandInteraction(event)

        verify(event).reply("You need manage roles permission to use this command.")
    }

    @Test
    fun `block user stores blocked user`() {
        stubAuthorizedCommand("block-user")
        whenever(event.getOption("user")).thenReturn(userOption)
        whenever(userOption.asUser).thenReturn(user)
        whenever(user.idLong).thenReturn(99L)
        whenever(user.asMention).thenReturn("<@99>")

        command.onSlashCommandInteraction(event)

        verify(reportSettingsService).blockUser(1L, 99L)
        verify(event).reply("<@99> can no longer report messages in this server.")
    }

    @Test
    fun `allow user removes blocked user`() {
        stubAuthorizedCommand("allow-user")
        whenever(event.getOption("user")).thenReturn(userOption)
        whenever(userOption.asUser).thenReturn(user)
        whenever(user.idLong).thenReturn(99L)
        whenever(user.asMention).thenReturn("<@99>")

        command.onSlashCommandInteraction(event)

        verify(reportSettingsService).allowUser(1L, 99L)
        verify(event).reply("<@99> can report messages in this server again.")
    }

    @Test
    fun `set urgent role stores role`() {
        stubAuthorizedCommand("set-urgent-role")
        whenever(event.getOption("role")).thenReturn(roleOption)
        whenever(roleOption.asRole).thenReturn(role)
        whenever(role.idLong).thenReturn(5L)
        whenever(role.asMention).thenReturn("<@&5>")

        command.onSlashCommandInteraction(event)

        verify(reportSettingsService).setUrgentRole(1L, 5L)
        verify(event).reply("Urgent reports will now mention <@&5>.")
    }

    @Test
    fun `clear urgent role removes role`() {
        stubAuthorizedCommand("clear-urgent-role")

        command.onSlashCommandInteraction(event)

        verify(reportSettingsService).clearUrgentRole(1L)
        verify(event).reply("Urgent report role cleared. Urgent reports will mention @everyone.")
    }

    @Test
    fun `set channel stores report channel`() {
        stubAuthorizedCommand("set-channel")
        whenever(member.hasPermission(Permission.MANAGE_CHANNEL)).thenReturn(true)
        whenever(event.getOption("channel")).thenReturn(channelOption)
        whenever(channelOption.asChannel).thenReturn(channelUnion)
        whenever(channelUnion.asTextChannel()).thenReturn(textChannel)
        whenever(textChannel.idLong).thenReturn(123L)
        whenever(textChannel.asMention).thenReturn("<#123>")

        command.onSlashCommandInteraction(event)

        verify(reportSettingsService).setReportChannel(1L, 123L)
        verify(event).reply("Message reports will now be sent to <#123>.")
    }

    @Test
    fun `set channel requires manage channel permission`() {
        stubAuthorizedCommandWithoutGuildId("set-channel")
        whenever(member.hasPermission(Permission.MANAGE_CHANNEL)).thenReturn(false)

        command.onSlashCommandInteraction(event)

        verify(event).reply("You need manage channel permission to use this command.")
        verify(reportSettingsService, never()).setReportChannel(any(), any())
    }

    @Test
    fun `clear channel removes report channel`() {
        stubAuthorizedCommand("clear-channel")
        whenever(member.hasPermission(Permission.MANAGE_CHANNEL)).thenReturn(true)

        command.onSlashCommandInteraction(event)

        verify(reportSettingsService).clearReportChannel(1L)
        verify(event).reply("Report channel cleared. Reports will use the moderator log channel.")
    }

    @Test
    fun `clear channel requires manage channel permission`() {
        stubAuthorizedCommandWithoutGuildId("clear-channel")
        whenever(member.hasPermission(Permission.MANAGE_CHANNEL)).thenReturn(false)

        command.onSlashCommandInteraction(event)

        verify(event).reply("You need manage channel permission to use this command.")
        verify(reportSettingsService, never()).clearReportChannel(any())
    }

    @Test
    fun `show displays reporting status, report channel, urgent role and blocked users`() {
        stubAuthorizedCommand("show")
        whenever(guild.name).thenReturn("Test Guild")
        whenever(guild.getTextChannelById(123L)).thenReturn(textChannel)
        whenever(textChannel.asMention).thenReturn("<#123>")
        whenever(reportSettingsService.getSettings(1L)).thenReturn(
            ReportSettings(
                1L,
                urgentRoleId = 5L,
                reportChannelId = 123L,
                enabled = true,
                blockedUserIds = mutableSetOf(99L)
            )
        )
        whenever(reportSettingsService.getUrgentMention(guild)).thenReturn("<@&5>")

        command.onSlashCommandInteraction(event)

        val replyCaptor = argumentCaptor<String>()
        verify(event).reply(replyCaptor.capture())
        assertTrue(replyCaptor.firstValue.contains("Report settings for Test Guild"))
        assertTrue(replyCaptor.firstValue.contains("- Reporting: enabled"))
        assertTrue(replyCaptor.firstValue.contains("- Report channel: <#123>"))
        assertTrue(replyCaptor.firstValue.contains("- Urgent mention: <@&5>"))
        assertTrue(replyCaptor.firstValue.contains("- Blocked users: <@99>"))
    }

    @Test
    fun `toggle enables reporting`() {
        stubAuthorizedCommand("toggle")
        whenever(reportSettingsService.toggleReporting(1L)).thenReturn(true)

        command.onSlashCommandInteraction(event)

        verify(event).reply("Message reporting is now enabled.")
    }

    @Test
    fun `toggle disables reporting`() {
        stubAuthorizedCommand("toggle")
        whenever(reportSettingsService.toggleReporting(1L)).thenReturn(false)

        command.onSlashCommandInteraction(event)

        verify(event).reply("Message reporting is now disabled.")
    }

    @Test
    fun `command data exposes expected subcommands and metadata`() {
        val commandData = command.getCommandsData().single()

        assertEquals("reportsettings", commandData.name)
        assertEquals(setOf(InteractionContextType.GUILD), commandData.contexts)
        assertEquals(
            listOf(
                "show",
                "block-user",
                "allow-user",
                "set-urgent-role",
                "clear-urgent-role",
                "set-channel",
                "clear-channel",
                "toggle"
            ),
            commandData.subcommands.map(SubcommandData::getName)
        )
    }

    private fun stubAuthorizedCommand(subcommandName: String) {
        stubGuildCommand(subcommandName)
        whenever(member.hasPermission(Permission.MANAGE_ROLES)).thenReturn(true)
        whenever(guild.idLong).thenReturn(1L)
    }

    private fun stubAuthorizedCommandWithoutGuildId(subcommandName: String) {
        stubGuildCommand(subcommandName)
        whenever(member.hasPermission(Permission.MANAGE_ROLES)).thenReturn(true)
    }

    private fun stubGuildCommand(subcommandName: String) {
        whenever(event.name).thenReturn("reportsettings")
        whenever(event.subcommandName).thenReturn(subcommandName)
        whenever(event.guild).thenReturn(guild)
        whenever(event.member).thenReturn(member)
        whenever(event.reply(any<String>())).thenReturn(replyAction)
        whenever(replyAction.setEphemeral(true)).thenReturn(replyAction)
    }

    private fun stubGuildCommandWithoutSubcommand() {
        whenever(event.name).thenReturn("reportsettings")
        whenever(event.guild).thenReturn(guild)
        whenever(event.member).thenReturn(member)
        whenever(event.reply(any<String>())).thenReturn(replyAction)
        whenever(replyAction.setEphemeral(true)).thenReturn(replyAction)
    }
}
