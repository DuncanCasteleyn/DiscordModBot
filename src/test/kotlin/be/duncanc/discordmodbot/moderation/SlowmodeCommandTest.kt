package be.duncanc.discordmodbot.moderation

import be.duncanc.discordmodbot.logging.GuildLogger
import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.Member
import net.dv8tion.jda.api.entities.SelfMember
import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.entities.User
import net.dv8tion.jda.api.entities.channel.ChannelType
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.interactions.InteractionContextType
import net.dv8tion.jda.api.interactions.InteractionHook
import net.dv8tion.jda.api.interactions.commands.OptionMapping
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData
import net.dv8tion.jda.api.managers.channel.concrete.TextChannelManager
import net.dv8tion.jda.api.requests.restaction.WebhookMessageEditAction
import net.dv8tion.jda.api.requests.restaction.interactions.ReplyCallbackAction
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.*
import java.util.function.Consumer

@ExtendWith(MockitoExtension::class)
class SlowmodeCommandTest {
    @Mock
    private lateinit var guildLogger: GuildLogger

    @Mock
    private lateinit var event: SlashCommandInteractionEvent

    @Mock
    private lateinit var guild: Guild

    @Mock
    private lateinit var member: Member

    @Mock
    private lateinit var selfMember: SelfMember

    @Mock
    private lateinit var user: User

    @Mock
    private lateinit var channel: TextChannel

    @Mock
    private lateinit var replyAction: ReplyCallbackAction

    @Mock
    private lateinit var manager: TextChannelManager

    @Mock
    private lateinit var hook: InteractionHook

    @Mock
    private lateinit var editAction: WebhookMessageEditAction<Message>

    @Mock
    private lateinit var minutesOption: OptionMapping

    private lateinit var command: TestSlowmodeCommand

    @BeforeEach
    fun setUp() {
        command = TestSlowmodeCommand(guildLogger)
        command.selectedChannel = channel
    }

    @Test
    fun `non-matching command name returns early`() {
        whenever(event.name).thenReturn("other")

        command.onSlashCommandInteraction(event)

        verify(event, never()).reply(any<String>())
    }

    @Test
    fun `member without manage channel permission is refused`() {
        stubContext()
        stubRefusalReply()
        whenever(member.hasPermission(channel, Permission.MANAGE_CHANNEL)).thenReturn(false)

        command.onSlashCommandInteraction(event)

        verify(event).reply("You need manage channels permission in this channel to use this command.")
    }

    @Test
    fun `bot without manage channel permission is refused`() {
        stubContext()
        stubRefusalReply()
        whenever(member.hasPermission(channel, Permission.MANAGE_CHANNEL)).thenReturn(true)
        whenever(guild.selfMember).thenReturn(selfMember)
        whenever(selfMember.hasPermission(channel, Permission.MANAGE_CHANNEL)).thenReturn(false)

        command.onSlashCommandInteraction(event)

        verify(event).reply("I need manage channels permission in this channel to use this command.")
    }

    @Test
    fun `set converts minutes to seconds and logs`() {
        stubAuthorized("set")
        stubMinutes(5)
        stubManager()
        stubReply()
        stubLogging()

        command.onSlashCommandInteraction(event)

        verify(manager).setSlowmode(300)
        verify(hook).editOriginal("Slowmode of 5 minute(s) set on <#5>.")
        verify(guildLogger).log(
            any<EmbedBuilder>(), eq(user), eq(guild), anyOrNull(), eq(GuildLogger.LogTypeAction.MODERATOR), anyOrNull()
        )
    }

    @Test
    fun `set accepts the 6 hour maximum`() {
        stubAuthorized("set")
        stubMinutes(360)
        stubManager()
        stubReply()
        stubLogging()

        command.onSlashCommandInteraction(event)

        verify(manager).setSlowmode(21_600)
    }

    @Test
    fun `set rejects zero minutes`() {
        stubAuthorized("set")
        stubMinutes(0)
        stubRefusalReply()

        command.onSlashCommandInteraction(event)

        verify(event).reply("Please provide a number of minutes between 1 and 360.")
        verify(channel, never()).manager
    }

    @Test
    fun `set rejects more than 360 minutes`() {
        stubAuthorized("set")
        stubMinutes(361)
        stubRefusalReply()

        command.onSlashCommandInteraction(event)

        verify(event).reply("Please provide a number of minutes between 1 and 360.")
        verify(channel, never()).manager
    }

    @Test
    fun `off clears slowmode and logs`() {
        stubAuthorized("off")
        stubManager()
        stubReply()
        stubLogging()

        command.onSlashCommandInteraction(event)

        verify(manager).setSlowmode(0)
        verify(hook).editOriginal("Slowmode removed from <#5>.")
        verify(guildLogger).log(
            any<EmbedBuilder>(), eq(user), eq(guild), anyOrNull(), eq(GuildLogger.LogTypeAction.MODERATOR), anyOrNull()
        )
    }

    @Test
    fun `command data exposes set and off restricted to text channels`() {
        val data = command.getCommandsData().single()

        assertEquals("slowmode", data.name)
        assertEquals(setOf(InteractionContextType.GUILD), data.contexts)
        assertEquals(listOf("set", "off"), data.subcommands.map(SubcommandData::getName))
        data.subcommands.forEach { subcommand ->
            val channelOption = subcommand.options.single { it.name == "channel" }
            assertEquals(setOf(ChannelType.TEXT), channelOption.channelTypes.toSet())
        }
        assertEquals(listOf("minutes", "channel"), data.subcommands.single { it.name == "set" }.options.map { it.name })
    }

    private fun stubContext() {
        whenever(event.name).thenReturn("slowmode")
        whenever(event.guild).thenReturn(guild)
        whenever(event.member).thenReturn(member)
    }

    private fun stubRefusalReply() {
        whenever(event.reply(any<String>())).thenReturn(replyAction)
        whenever(replyAction.setEphemeral(true)).thenReturn(replyAction)
    }

    private fun stubReply() {
        whenever(event.deferReply(true)).thenReturn(replyAction)
        doAnswer { invocation ->
            invocation.component1<Consumer<InteractionHook>>().accept(hook)
            null
        }.whenever(replyAction).queue(any())
        whenever(hook.editOriginal(any<String>())).thenReturn(editAction)
        whenever(channel.asMention).thenReturn("<#5>")
    }

    private fun stubAuthorized(subcommand: String) {
        stubContext()
        whenever(member.hasPermission(channel, Permission.MANAGE_CHANNEL)).thenReturn(true)
        whenever(guild.selfMember).thenReturn(selfMember)
        whenever(selfMember.hasPermission(channel, Permission.MANAGE_CHANNEL)).thenReturn(true)
        whenever(event.subcommandName).thenReturn(subcommand)
    }

    private fun stubMinutes(minutes: Long) {
        whenever(event.getOption("minutes")).thenReturn(minutesOption)
        whenever(minutesOption.asLong).thenReturn(minutes)
    }

    private fun stubManager() {
        whenever(channel.manager).thenReturn(manager)
        whenever(manager.setSlowmode(any())).thenReturn(manager)
        whenever(manager.reason(any<String>())).thenReturn(manager)
        doAnswer { invocation ->
            invocation.component1<Consumer<Void?>>().accept(null)
            null
        }.whenever(manager).queue(any(), any())
    }

    private fun stubLogging() {
        whenever(event.user).thenReturn(user)
        whenever(member.user).thenReturn(user)
        whenever(user.name).thenReturn("moderator")
    }

    private class TestSlowmodeCommand(guildLogger: GuildLogger) : SlowmodeCommand(guildLogger) {
        var selectedChannel: TextChannel? = null

        override fun resolveChannel(event: SlashCommandInteractionEvent): TextChannel? = selectedChannel
    }
}
