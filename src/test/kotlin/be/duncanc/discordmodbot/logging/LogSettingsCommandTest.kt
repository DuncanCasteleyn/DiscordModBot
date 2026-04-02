package be.duncanc.discordmodbot.logging

import be.duncanc.discordmodbot.logging.persistence.LoggingSettings
import be.duncanc.discordmodbot.logging.persistence.LoggingSettingsRepository
import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.Member
import net.dv8tion.jda.api.entities.channel.ChannelType
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel
import net.dv8tion.jda.api.events.guild.GuildLeaveEvent
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
import org.mockito.Mockito.lenient
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.*
import java.util.*

@ExtendWith(MockitoExtension::class)
class LogSettingsCommandTest {
    @Mock
    private lateinit var loggingSettingsRepository: LoggingSettingsRepository

    @Mock
    private lateinit var slashEvent: SlashCommandInteractionEvent

    @Mock
    private lateinit var guildLeaveEvent: GuildLeaveEvent

    @Mock
    private lateinit var guild: Guild

    @Mock
    private lateinit var member: Member

    @Mock
    private lateinit var modChannel: TextChannel

    @Mock
    private lateinit var userChannel: TextChannel

    @Mock
    private lateinit var replyAction: ReplyCallbackAction

    private lateinit var command: TestLogSettingsCommand

    @BeforeEach
    fun setUp() {
        command = TestLogSettingsCommand(loggingSettingsRepository)

        lenient().whenever(slashEvent.reply(any<String>())).thenReturn(replyAction)
        lenient().whenever(replyAction.setEphemeral(true)).thenReturn(replyAction)

        lenient().whenever(slashEvent.guild).thenReturn(guild)
        lenient().whenever(slashEvent.member).thenReturn(member)
        lenient().whenever(guild.idLong).thenReturn(1L)
        lenient().whenever(guild.name).thenReturn("Test Guild")
        lenient().whenever(member.hasPermission(Permission.MANAGE_CHANNEL)).thenReturn(true)
        lenient().whenever(slashEvent.name).thenReturn("logsettings")
    }

    @Test
    fun `non-matching command name returns early`() {
        whenever(slashEvent.name).thenReturn("othercommand")

        command.onSlashCommandInteraction(slashEvent)

        verify(slashEvent, never()).reply(any<String>())
    }

    @Test
    fun `missing member returns guild error`() {
        whenever(slashEvent.member).thenReturn(null)

        command.onSlashCommandInteraction(slashEvent)

        verify(slashEvent).reply("This command only works in a guild.")
    }

    @Test
    fun `missing manage channel permission returns error`() {
        whenever(member.hasPermission(Permission.MANAGE_CHANNEL)).thenReturn(false)

        command.onSlashCommandInteraction(slashEvent)

        verify(slashEvent).reply("You need manage channel permission to use this command.")
    }

    @Test
    fun `show displays default settings`() {
        whenever(slashEvent.subcommandName).thenReturn("show")
        whenever(loggingSettingsRepository.findById(1L)).thenReturn(Optional.empty())

        command.onSlashCommandInteraction(slashEvent)

        val replyCaptor = argumentCaptor<String>()
        verify(slashEvent).reply(replyCaptor.capture())
        val message = replyCaptor.firstValue
        assertTrue(message.contains("Logging settings for Test Guild"))
        assertTrue(message.contains("- Moderator log channel: Not configured"))
        assertTrue(message.contains("- User log channel: Using moderator log channel"))
        assertTrue(message.contains("- Edited messages: enabled"))
        assertTrue(message.contains("- Member unbans: enabled"))
    }

    @Test
    fun `show displays configured channels and missing channels`() {
        whenever(slashEvent.subcommandName).thenReturn("show")
        whenever(loggingSettingsRepository.findById(1L)).thenReturn(
            Optional.of(
                LoggingSettings(
                    guildId = 1L,
                    modLogChannel = 11L,
                    userLogChannel = 12L,
                    logMessageUpdate = false,
                    logMessageDelete = false,
                    logMemberJoin = false,
                    logMemberLeave = false,
                    logMemberBan = false,
                    logMemberRemoveBan = false
                )
            )
        )
        whenever(guild.getTextChannelById(11L)).thenReturn(modChannel)
        whenever(guild.getTextChannelById(12L)).thenReturn(null)
        whenever(modChannel.asMention).thenReturn("<#11>")

        command.onSlashCommandInteraction(slashEvent)

        val replyCaptor = argumentCaptor<String>()
        verify(slashEvent).reply(replyCaptor.capture())
        val message = replyCaptor.firstValue
        assertTrue(message.contains("- Moderator log channel: <#11>"))
        assertTrue(message.contains("- User log channel: Channel not found (ID: 12)"))
        assertTrue(message.contains("- Member bans: disabled"))
    }

    @Test
    fun `set mod channel stores selected channel`() {
        whenever(slashEvent.subcommandName).thenReturn("set-mod-channel")
        whenever(loggingSettingsRepository.findById(1L)).thenReturn(Optional.empty())
        whenever(modChannel.idLong).thenReturn(11L)
        whenever(modChannel.asMention).thenReturn("<#11>")
        command.selectedChannel = modChannel

        command.onSlashCommandInteraction(slashEvent)

        val settingsCaptor = argumentCaptor<LoggingSettings>()
        verify(loggingSettingsRepository).save(settingsCaptor.capture())
        assertEquals(11L, settingsCaptor.firstValue.modLogChannel)
        verify(slashEvent).reply("Moderator log channel set to <#11>.")
    }

    @Test
    fun `set user channel stores selected channel`() {
        whenever(slashEvent.subcommandName).thenReturn("set-user-channel")
        whenever(loggingSettingsRepository.findById(1L)).thenReturn(Optional.empty())
        whenever(userChannel.idLong).thenReturn(12L)
        whenever(userChannel.asMention).thenReturn("<#12>")
        command.selectedChannel = userChannel

        command.onSlashCommandInteraction(slashEvent)

        val settingsCaptor = argumentCaptor<LoggingSettings>()
        verify(loggingSettingsRepository).save(settingsCaptor.capture())
        assertEquals(12L, settingsCaptor.firstValue.userLogChannel)
        verify(slashEvent).reply("User log channel set to <#12>.")
    }

    @Test
    fun `toggle message updates flips setting`() {
        assertToggle(
            subcommand = "toggle-message-updates",
            initialSettings = LoggingSettings(1L, logMessageUpdate = true),
            expectedMessage = "Message update logging is now disabled.",
            verifySetting = { assertEquals(false, it.logMessageUpdate) }
        )
    }

    @Test
    fun `toggle message deletes flips setting`() {
        assertToggle(
            subcommand = "toggle-message-deletes",
            initialSettings = LoggingSettings(1L, logMessageDelete = true),
            expectedMessage = "Message delete logging is now disabled.",
            verifySetting = { assertEquals(false, it.logMessageDelete) }
        )
    }

    @Test
    fun `toggle member joins flips setting`() {
        assertToggle(
            subcommand = "toggle-member-joins",
            initialSettings = LoggingSettings(1L, logMemberJoin = true),
            expectedMessage = "Member join logging is now disabled.",
            verifySetting = { assertEquals(false, it.logMemberJoin) }
        )
    }

    @Test
    fun `toggle member leaves flips setting`() {
        assertToggle(
            subcommand = "toggle-member-leaves",
            initialSettings = LoggingSettings(1L, logMemberLeave = true),
            expectedMessage = "Member leave logging is now disabled.",
            verifySetting = { assertEquals(false, it.logMemberLeave) }
        )
    }

    @Test
    fun `toggle member bans flips setting`() {
        assertToggle(
            subcommand = "toggle-member-bans",
            initialSettings = LoggingSettings(1L, logMemberBan = true),
            expectedMessage = "Member ban logging is now disabled.",
            verifySetting = { assertEquals(false, it.logMemberBan) }
        )
    }

    @Test
    fun `toggle member unbans flips remove ban setting`() {
        assertToggle(
            subcommand = "toggle-member-unbans",
            initialSettings = LoggingSettings(1L, logMemberRemoveBan = true),
            expectedMessage = "Member unban logging is now disabled.",
            verifySetting = { assertEquals(false, it.logMemberRemoveBan) }
        )
    }

    @Test
    fun `guild leave removes stored settings`() {
        whenever(guildLeaveEvent.guild).thenReturn(guild)

        command.onGuildLeave(guildLeaveEvent)

        verify(loggingSettingsRepository).deleteById(1L)
    }

    @Test
    fun `command data exposes expected subcommands and metadata`() {
        val commandData = command.getCommandsData().single()

        assertEquals("logsettings", commandData.name)
        assertEquals(setOf(InteractionContextType.GUILD), commandData.contexts)
        assertEquals(
            listOf(
                "show",
                "set-mod-channel",
                "set-user-channel",
                "toggle-message-updates",
                "toggle-message-deletes",
                "toggle-member-joins",
                "toggle-member-leaves",
                "toggle-member-bans",
                "toggle-member-unbans"
            ),
            commandData.subcommands.map(SubcommandData::getName)
        )

        val channelOption = commandData.subcommands.single { it.name == "set-mod-channel" }.options.single()
        assertEquals(ChannelType.TEXT, channelOption.channelTypes.single())
    }

    private fun assertToggle(
        subcommand: String,
        initialSettings: LoggingSettings,
        expectedMessage: String,
        verifySetting: (LoggingSettings) -> Unit
    ) {
        whenever(slashEvent.subcommandName).thenReturn(subcommand)
        whenever(loggingSettingsRepository.findById(1L)).thenReturn(Optional.of(initialSettings))

        command.onSlashCommandInteraction(slashEvent)

        val settingsCaptor = argumentCaptor<LoggingSettings>()
        verify(loggingSettingsRepository).save(settingsCaptor.capture())
        verifySetting(settingsCaptor.firstValue)
        verify(slashEvent).reply(expectedMessage)
    }

    private class TestLogSettingsCommand(
        loggingSettingsRepository: LoggingSettingsRepository
    ) : LogSettingsCommand(loggingSettingsRepository) {
        var selectedChannel: TextChannel? = null

        override fun getRequiredTextChannel(event: SlashCommandInteractionEvent): TextChannel? {
            return selectedChannel ?: super.getRequiredTextChannel(event)
        }
    }
}
