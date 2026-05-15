package be.duncanc.discordmodbot.moderation

import be.duncanc.discordmodbot.moderation.persistence.GuildWarnPointsRepository
import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.Member
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
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
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@ExtendWith(MockitoExtension::class)
class WarnHistoryCommandTest {
    @Mock
    private lateinit var guildWarnPointsRepository: GuildWarnPointsRepository

    @Mock
    private lateinit var slashEvent: SlashCommandInteractionEvent

    @Mock
    private lateinit var guild: Guild

    @Mock
    private lateinit var member: Member

    @Mock
    private lateinit var replyAction: ReplyCallbackAction

    private lateinit var command: WarnHistoryCommand

    @BeforeEach
    fun setUp() {
        command = WarnHistoryCommand(guildWarnPointsRepository)
    }

    @Test
    fun `missing user option returns an error`() {
        whenever(slashEvent.name).thenReturn("warnhistory")
        whenever(slashEvent.subcommandName).thenReturn("active")
        whenever(slashEvent.guild).thenReturn(guild)
        whenever(slashEvent.member).thenReturn(member)
        whenever(member.hasPermission(Permission.MANAGE_ROLES)).thenReturn(true)
        whenever(slashEvent.getOption("user")).thenReturn(null)
        whenever(slashEvent.reply(any<String>())).thenReturn(replyAction)
        whenever(replyAction.setEphemeral(true)).thenReturn(replyAction)

        command.onSlashCommandInteraction(slashEvent)

        verify(slashEvent).reply("This command requires a user.")
    }

    @Test
    fun `missing subcommand returns an error`() {
        whenever(slashEvent.name).thenReturn("warnhistory")
        whenever(slashEvent.subcommandName).thenReturn(null)
        whenever(slashEvent.guild).thenReturn(guild)
        whenever(slashEvent.member).thenReturn(member)
        whenever(member.hasPermission(Permission.MANAGE_ROLES)).thenReturn(true)
        whenever(slashEvent.reply(any<String>())).thenReturn(replyAction)
        whenever(replyAction.setEphemeral(true)).thenReturn(replyAction)

        command.onSlashCommandInteraction(slashEvent)

        verify(slashEvent).reply("Please choose a valid /warnhistory subcommand.")
    }

    @Test
    fun `command data exposes active and all subcommands`() {
        val commandData = command.getCommandsData().single()

        assertEquals(listOf("active", "all"), commandData.subcommands.map(SubcommandData::getName))
        commandData.subcommands.forEach { subcommand ->
            val userOption = subcommand.options.single { it.name == "user" }
            assertTrue(userOption.isRequired)
        }
    }
}
