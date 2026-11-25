package be.duncanc.discordmodbot.moderation

import be.duncanc.discordmodbot.logging.GuildLogger
import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.Member
import net.dv8tion.jda.api.entities.SelfMember
import net.dv8tion.jda.api.entities.User
import net.dv8tion.jda.api.entities.channel.ChannelType
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel
import net.dv8tion.jda.api.entities.channel.middleman.StandardGuildMessageChannel
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.interactions.InteractionContextType
import net.dv8tion.jda.api.interactions.InteractionHook
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData
import net.dv8tion.jda.api.requests.restaction.WebhookMessageEditAction
import net.dv8tion.jda.api.requests.restaction.interactions.ReplyCallbackAction
import net.dv8tion.jda.api.entities.Message
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.*
import java.util.concurrent.CompletableFuture
import java.util.function.Consumer

@ExtendWith(MockitoExtension::class)
class LockdownCommandTest {
    @Mock
    private lateinit var lockdownService: LockdownService

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
    private lateinit var hook: InteractionHook

    @Mock
    private lateinit var editAction: WebhookMessageEditAction<Message>

    private lateinit var command: TestLockdownCommand

    @BeforeEach
    fun setUp() {
        command = TestLockdownCommand(lockdownService, guildLogger)
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
        verifyNoInteractions(lockdownService)
    }

    @Test
    fun `bot without manage permissions is refused`() {
        stubContext()
        stubRefusalReply()
        whenever(member.hasPermission(channel, Permission.MANAGE_CHANNEL)).thenReturn(true)
        whenever(guild.selfMember).thenReturn(selfMember)
        whenever(selfMember.hasPermission(channel, Permission.MANAGE_CHANNEL, Permission.MANAGE_PERMISSIONS))
            .thenReturn(false)

        command.onSlashCommandInteraction(event)

        verify(event).reply(
            "I need manage channels and manage permissions permissions in this channel to use this command."
        )
        verifyNoInteractions(lockdownService)
    }

    @Test
    fun `lock on locks the channel, reports skipped roles and logs it`() {
        stubAuthorized("on")
        stubLogging()
        whenever(lockdownService.lock(channel))
            .thenReturn(CompletableFuture.completedFuture(LockdownService.LockResult.Locked(listOf(7L), listOf(8L))))

        command.onSlashCommandInteraction(event)

        val reply = argumentCaptor<String>()
        verify(hook).editOriginal(reply.capture())
        assertTrue(reply.firstValue.startsWith("<#5> is locked down"))
        assertTrue(reply.firstValue.contains("<@&7>"))
        assertTrue(reply.firstValue.contains("<@8>"))
        verify(guildLogger).log(
            any<EmbedBuilder>(), eq(user), eq(guild), anyOrNull(), eq(GuildLogger.LogTypeAction.MODERATOR), anyOrNull()
        )
    }

    @Test
    fun `lock on reports an already locked channel without logging`() {
        stubAuthorized("on")
        whenever(lockdownService.lock(channel))
            .thenReturn(CompletableFuture.completedFuture(LockdownService.LockResult.AlreadyLocked))

        command.onSlashCommandInteraction(event)

        verify(hook).editOriginal("<#5> is already locked down.")
        verifyNoInteractions(guildLogger)
    }

    @Test
    fun `lock off on an unlocked channel reports so without logging`() {
        stubAuthorized("off")
        whenever(lockdownService.unlock(channel))
            .thenReturn(CompletableFuture.completedFuture(LockdownService.UnlockResult.NotLocked))

        command.onSlashCommandInteraction(event)

        verify(hook).editOriginal("<#5> is not locked down.")
        verifyNoInteractions(guildLogger)
    }

    @Test
    fun `lock off unlocks the channel and logs it`() {
        stubAuthorized("off")
        stubLogging()
        whenever(lockdownService.unlock(channel))
            .thenReturn(CompletableFuture.completedFuture(LockdownService.UnlockResult.Unlocked(emptyList())))

        command.onSlashCommandInteraction(event)

        verify(hook).editOriginal("<#5> is unlocked.")
        verify(guildLogger).log(
            any<EmbedBuilder>(), eq(user), eq(guild), anyOrNull(), eq(GuildLogger.LogTypeAction.MODERATOR), anyOrNull()
        )
    }

    @Test
    fun `command data exposes on and off with a text and announcement channel option`() {
        val data = command.getCommandsData().single()

        assertEquals("lockdown", data.name)
        assertEquals(setOf(InteractionContextType.GUILD), data.contexts)
        assertEquals(listOf("on", "off"), data.subcommands.map(SubcommandData::getName))
        data.subcommands.forEach {
            assertEquals(setOf(ChannelType.TEXT, ChannelType.NEWS), it.options.single().channelTypes.toSet())
        }
    }

    private fun stubContext() {
        whenever(event.name).thenReturn("lockdown")
        whenever(event.guild).thenReturn(guild)
        whenever(event.member).thenReturn(member)
    }

    private fun stubRefusalReply() {
        whenever(event.reply(any<String>())).thenReturn(replyAction)
        whenever(replyAction.setEphemeral(true)).thenReturn(replyAction)
    }

    private fun stubAuthorized(subcommand: String) {
        stubContext()
        whenever(member.hasPermission(channel, Permission.MANAGE_CHANNEL)).thenReturn(true)
        whenever(guild.selfMember).thenReturn(selfMember)
        whenever(selfMember.hasPermission(channel, Permission.MANAGE_CHANNEL, Permission.MANAGE_PERMISSIONS))
            .thenReturn(true)
        whenever(event.subcommandName).thenReturn(subcommand)
        whenever(channel.asMention).thenReturn("<#5>")
        whenever(event.deferReply(true)).thenReturn(replyAction)
        doAnswer { invocation ->
            invocation.component1<Consumer<InteractionHook>>().accept(hook)
            null
        }.whenever(replyAction).queue(any())
        whenever(hook.editOriginal(any<String>())).thenReturn(editAction)
    }

    private fun stubLogging() {
        whenever(event.user).thenReturn(user)
        whenever(member.user).thenReturn(user)
        whenever(user.name).thenReturn("moderator")
    }

    private class TestLockdownCommand(
        lockdownService: LockdownService,
        guildLogger: GuildLogger
    ) : LockdownCommand(lockdownService, guildLogger) {
        var selectedChannel: StandardGuildMessageChannel? = null

        override fun resolveChannel(event: SlashCommandInteractionEvent): StandardGuildMessageChannel? = selectedChannel
    }
}
