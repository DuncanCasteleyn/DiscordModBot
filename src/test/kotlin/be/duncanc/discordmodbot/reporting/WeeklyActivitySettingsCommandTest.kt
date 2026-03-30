package be.duncanc.discordmodbot.reporting

import be.duncanc.discordmodbot.reporting.persistence.ActivityReportSettings
import be.duncanc.discordmodbot.reporting.persistence.ActivityReportSettingsRepository
import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.Member
import net.dv8tion.jda.api.entities.Role
import net.dv8tion.jda.api.entities.User
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.interactions.InteractionContextType
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData
import net.dv8tion.jda.api.requests.restaction.interactions.ReplyCallbackAction
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.Mockito.lenient
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.*

@ExtendWith(MockitoExtension::class)
class WeeklyActivitySettingsCommandTest {
    @Mock
    private lateinit var activityReportSettingsRepository: ActivityReportSettingsRepository

    @Mock
    private lateinit var slashEvent: SlashCommandInteractionEvent

    @Mock
    private lateinit var guild: Guild

    @Mock
    private lateinit var member: Member

    @Mock
    private lateinit var textChannel: TextChannel

    @Mock
    private lateinit var role: Role

    @Mock
    private lateinit var user: User

    @Mock
    private lateinit var replyAction: ReplyCallbackAction

    private lateinit var command: TestWeeklyActivitySettingsCommand

    @BeforeEach
    fun setUp() {
        command = TestWeeklyActivitySettingsCommand(activityReportSettingsRepository)

        lenient().whenever(slashEvent.reply(any<String>())).thenReturn(replyAction)
        lenient().whenever(replyAction.setEphemeral(true)).thenReturn(replyAction)
        lenient().whenever(slashEvent.name).thenReturn("weeklyactivitysettings")
        lenient().whenever(slashEvent.guild).thenReturn(guild)
        lenient().whenever(slashEvent.member).thenReturn(member)
        lenient().whenever(guild.idLong).thenReturn(1L)
        lenient().whenever(guild.name).thenReturn("Test Guild")
        lenient().whenever(member.hasPermission(Permission.ADMINISTRATOR)).thenReturn(true)
        lenient().whenever(activityReportSettingsRepository.findById(1L)).thenReturn(Optional.empty())
    }

    @Test
    fun `missing administrator permission returns error`() {
        whenever(member.hasPermission(Permission.ADMINISTRATOR)).thenReturn(false)

        command.onSlashCommandInteraction(slashEvent)

        verify(slashEvent).reply("You need administrator permission to use this command.")
    }

    @Test
    fun `set channel stores report channel`() {
        whenever(slashEvent.subcommandName).thenReturn("set-channel")
        whenever(textChannel.idLong).thenReturn(11L)
        whenever(textChannel.asMention).thenReturn("<#11>")
        command.selectedChannel = textChannel

        command.onSlashCommandInteraction(slashEvent)

        val settingsCaptor = argumentCaptor<ActivityReportSettings>()
        verify(activityReportSettingsRepository).save(settingsCaptor.capture())
        assertEquals(11L, settingsCaptor.firstValue.reportChannel)
        verify(slashEvent).reply("Weekly activity report channel set to <#11>.")
    }

    @Test
    fun `add role stores tracked role id`() {
        whenever(slashEvent.subcommandName).thenReturn("add-role")
        whenever(role.idLong).thenReturn(15L)
        whenever(role.asMention).thenReturn("<@&15>")
        command.selectedRole = role

        command.onSlashCommandInteraction(slashEvent)

        val settingsCaptor = argumentCaptor<ActivityReportSettings>()
        verify(activityReportSettingsRepository).save(settingsCaptor.capture())
        assertEquals(setOf(15L), settingsCaptor.firstValue.trackedRoleOrMember)
        verify(slashEvent).reply("Added <@&15> to the weekly activity report tracking list.")
    }

    @Test
    fun `add member stores tracked member id`() {
        whenever(slashEvent.subcommandName).thenReturn("add-member")
        whenever(user.idLong).thenReturn(99L)
        whenever(user.asMention).thenReturn("<@99>")
        command.selectedUser = user

        command.onSlashCommandInteraction(slashEvent)

        val settingsCaptor = argumentCaptor<ActivityReportSettings>()
        verify(activityReportSettingsRepository).save(settingsCaptor.capture())
        assertEquals(setOf(99L), settingsCaptor.firstValue.trackedRoleOrMember)
        verify(slashEvent).reply("Added <@99> to the weekly activity report tracking list.")
    }

    @Test
    fun `remove role updates existing settings`() {
        whenever(slashEvent.subcommandName).thenReturn("remove-role")
        whenever(role.idLong).thenReturn(15L)
        whenever(role.asMention).thenReturn("<@&15>")
        whenever(activityReportSettingsRepository.findById(1L)).thenReturn(
            Optional.of(ActivityReportSettings(1L, trackedRoleOrMember = mutableSetOf(15L, 99L)))
        )
        command.selectedRole = role

        command.onSlashCommandInteraction(slashEvent)

        val settingsCaptor = argumentCaptor<ActivityReportSettings>()
        verify(activityReportSettingsRepository).save(settingsCaptor.capture())
        assertEquals(setOf(99L), settingsCaptor.firstValue.trackedRoleOrMember)
        verify(slashEvent).reply("Removed <@&15> from the weekly activity report tracking list.")
    }

    @Test
    fun `command data exposes expected subcommands`() {
        val commandData = command.getCommandsData().single()

        assertEquals("weeklyactivitysettings", commandData.name)
        assertEquals(setOf(InteractionContextType.GUILD), commandData.contexts)
        assertEquals(
            listOf("show", "set-channel", "add-role", "remove-role", "add-member", "remove-member"),
            commandData.subcommands.map(SubcommandData::getName)
        )
    }

    private class TestWeeklyActivitySettingsCommand(
        activityReportSettingsRepository: ActivityReportSettingsRepository
    ) : WeeklyActivitySettingsCommand(activityReportSettingsRepository) {
        var selectedChannel: TextChannel? = null
        var selectedRole: Role? = null
        var selectedUser: User? = null

        override fun getRequiredTextChannel(event: SlashCommandInteractionEvent): TextChannel? {
            return selectedChannel ?: super.getRequiredTextChannel(event)
        }

        override fun getRequiredRole(event: SlashCommandInteractionEvent): Role? {
            return selectedRole ?: super.getRequiredRole(event)
        }

        override fun getRequiredUser(event: SlashCommandInteractionEvent): User? {
            return selectedUser ?: super.getRequiredUser(event)
        }
    }
}
