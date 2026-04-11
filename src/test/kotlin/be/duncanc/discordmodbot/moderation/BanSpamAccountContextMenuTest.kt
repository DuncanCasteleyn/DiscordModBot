package be.duncanc.discordmodbot.moderation

import be.duncanc.discordmodbot.logging.GuildLogger
import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.Member
import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.entities.MessageEmbed
import net.dv8tion.jda.api.entities.SelfUser
import net.dv8tion.jda.api.entities.channel.concrete.PrivateChannel
import net.dv8tion.jda.api.entities.User
import net.dv8tion.jda.api.events.interaction.command.MessageContextInteractionEvent
import net.dv8tion.jda.api.events.interaction.command.UserContextInteractionEvent
import net.dv8tion.jda.api.interactions.InteractionHook
import net.dv8tion.jda.api.interactions.commands.Command
import net.dv8tion.jda.api.requests.restaction.AuditableRestAction
import net.dv8tion.jda.api.requests.restaction.CacheRestAction
import net.dv8tion.jda.api.requests.restaction.MessageCreateAction
import net.dv8tion.jda.api.requests.restaction.WebhookMessageEditAction
import net.dv8tion.jda.api.requests.restaction.interactions.ReplyCallbackAction
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.concurrent.TimeUnit
import java.util.function.Consumer

@ExtendWith(MockitoExtension::class)
class BanSpamAccountContextMenuTest {
    @Mock
    private lateinit var guildLogger: GuildLogger

    @Mock
    private lateinit var event: UserContextInteractionEvent

    @Mock
    private lateinit var messageEvent: MessageContextInteractionEvent

    @Mock
    private lateinit var moderator: Member

    @Mock
    private lateinit var moderatorUser: User

    @Mock
    private lateinit var targetMember: Member

    @Mock
    private lateinit var targetUser: User

    @Mock
    private lateinit var guild: Guild

    @Mock
    private lateinit var jda: JDA

    @Mock
    private lateinit var selfUser: SelfUser

    @Mock
    private lateinit var replyAction: ReplyCallbackAction

    @Mock
    private lateinit var hook: InteractionHook

    @Mock
    private lateinit var editAction: WebhookMessageEditAction<Message>

    @Mock
    private lateinit var openPrivateChannelAction: CacheRestAction<PrivateChannel>

    @Mock
    private lateinit var privateChannel: PrivateChannel

    @Mock
    private lateinit var messageCreateAction: MessageCreateAction

    @Mock
    private lateinit var banRestAction: AuditableRestAction<Void>

    @Mock
    private lateinit var dmMessage: Message

    @Mock
    private lateinit var selectedMessage: Message

    private lateinit var command: BanSpamAccountContextMenu

    @BeforeEach
    fun setUp() {
        command = BanSpamAccountContextMenu(guildLogger)
    }

    @Test
    fun `non-matching command name returns early`() {
        whenever(event.name).thenReturn("Other Command")

        command.onUserContextInteraction(event)

        verify(event, never()).reply(any<String>())
        verify(event, never()).deferReply(true)
    }

    @Test
    fun `missing ban permission replies with an error`() {
        whenever(event.name).thenReturn("Ban Spam Account")
        whenever(event.member).thenReturn(moderator)
        whenever(moderator.hasPermission(Permission.BAN_MEMBERS)).thenReturn(false)
        whenever(event.reply(any<String>())).thenReturn(replyAction)
        whenever(replyAction.setEphemeral(true)).thenReturn(replyAction)

        command.onUserContextInteraction(event)

        verify(event).reply("You need ban members permission to use this command.")
    }

    @Test
    fun `missing target member replies with an error`() {
        whenever(event.name).thenReturn("Ban Spam Account")
        whenever(event.member).thenReturn(moderator)
        whenever(moderator.hasPermission(Permission.BAN_MEMBERS)).thenReturn(true)
        whenever(event.targetMember).thenReturn(null)
        whenever(event.reply(any<String>())).thenReturn(replyAction)
        whenever(replyAction.setEphemeral(true)).thenReturn(replyAction)

        command.onUserContextInteraction(event)

        verify(event).reply("You need to select a user that is still in the server.")
    }

    @Test
    fun `interaction hierarchy failure replies with an error`() {
        whenever(event.name).thenReturn("Ban Spam Account")
        whenever(event.member).thenReturn(moderator)
        whenever(event.jda).thenReturn(jda)
        whenever(jda.selfUser).thenReturn(selfUser)
        whenever(selfUser.idLong).thenReturn(999L)
        whenever(moderator.hasPermission(Permission.BAN_MEMBERS)).thenReturn(true)
        whenever(event.targetMember).thenReturn(targetMember)
        whenever(targetMember.idLong).thenReturn(1L)
        whenever(moderator.canInteract(targetMember)).thenReturn(false)
        whenever(event.reply(any<String>())).thenReturn(replyAction)
        whenever(replyAction.setEphemeral(true)).thenReturn(replyAction)

        command.onUserContextInteraction(event)

        verify(event).reply("You can't ban a user that you can't interact with.")
    }

    @Test
    fun `self user target replies with an error`() {
        whenever(event.name).thenReturn("Ban Spam Account")
        whenever(event.member).thenReturn(moderator)
        whenever(event.jda).thenReturn(jda)
        whenever(jda.selfUser).thenReturn(selfUser)
        whenever(selfUser.idLong).thenReturn(1L)
        whenever(moderator.hasPermission(Permission.BAN_MEMBERS)).thenReturn(true)
        whenever(event.targetMember).thenReturn(targetMember)
        whenever(targetMember.idLong).thenReturn(1L)
        whenever(event.reply(any<String>())).thenReturn(replyAction)
        whenever(replyAction.setEphemeral(true)).thenReturn(replyAction)

        command.onUserContextInteraction(event)

        verify(event).reply("You can't ban the bot itself.")
        verify(moderator, never()).canInteract(targetMember)
        verify(event, never()).deferReply(true)
    }

    @Test
    fun `successful dm and ban uses compromised account reason with 24 hour cleanup`() {
        val embeds = listOf<MessageEmbed>()
        stubSuccessfulBanFlow(embeds)

        command.onUserContextInteraction(event)

        verify(guild).ban(targetMember, 1, TimeUnit.DAYS)
        verify(banRestAction).reason("Compromised account")
        verify(hook).editOriginal("Banned $targetMember.\n\nThe following message was sent to the user:")
        verify(editAction).setEmbeds(embeds)
        verify(guildLogger).log(
            any<EmbedBuilder>(),
            eq(targetUser),
            eq(guild),
            isNull<List<MessageEmbed>>(),
            eq(GuildLogger.LogTypeAction.MODERATOR),
            isNull()
        )
    }

    @Test
    fun `dm failure still bans the user`() {
        stubUserContextMenuStart()
        whenever(targetMember.user).thenReturn(targetUser)
        whenever(targetUser.openPrivateChannel()).thenReturn(openPrivateChannelAction)
        doAnswer {
            val failure = it.arguments[1] as Consumer<Throwable>
            failure.accept(IllegalStateException("DMs disabled"))
            null
        }.whenever(openPrivateChannelAction).queue(any<Consumer<PrivateChannel>>(), any<Consumer<Throwable>>())
        whenever(guild.ban(targetMember, 1, TimeUnit.DAYS)).thenReturn(banRestAction)
        whenever(banRestAction.reason("Compromised account")).thenReturn(banRestAction)
        doAnswer {
            val success = it.arguments[0] as Consumer<Void?>
            success.accept(null)
            null
        }.whenever(banRestAction).queue(any<Consumer<Void?>>(), any<Consumer<Throwable>>())
        whenever(hook.editOriginal(any<String>())).thenReturn(editAction)

        command.onUserContextInteraction(event)

        verify(guild).ban(targetMember, 1, TimeUnit.DAYS)
        verify(hook).editOriginal(
            """Banned $targetMember.

Was unable to send a DM to the user please inform the user manually, if possible.
Error: DMs disabled"""
        )
    }

    @Test
    fun `message context menu bans the selected message author`() {
        val embeds = listOf<MessageEmbed>()
        stubSuccessfulMessageBanFlow(embeds)

        command.onMessageContextInteraction(messageEvent)

        verify(guild).ban(targetMember, 1, TimeUnit.DAYS)
        verify(banRestAction).reason("Compromised account")
        verify(hook).editOriginal("Banned $targetMember.\n\nThe following message was sent to the user:")
        verify(editAction).setEmbeds(embeds)
    }

    @Test
    fun `message context menu requires the author to still be in the server`() {
        whenever(messageEvent.name).thenReturn("Ban Spam Account")
        whenever(messageEvent.member).thenReturn(moderator)
        whenever(moderator.hasPermission(Permission.BAN_MEMBERS)).thenReturn(true)
        whenever(messageEvent.target).thenReturn(selectedMessage)
        whenever(selectedMessage.member).thenReturn(null)
        whenever(messageEvent.reply(any<String>())).thenReturn(replyAction)
        whenever(replyAction.setEphemeral(true)).thenReturn(replyAction)

        command.onMessageContextInteraction(messageEvent)

        verify(messageEvent).reply("You need to select a user that is still in the server.")
        verify(messageEvent, never()).deferReply(true)
    }

    @Test
    fun `message context menu rejects targeting the bot`() {
        whenever(messageEvent.name).thenReturn("Ban Spam Account")
        whenever(messageEvent.member).thenReturn(moderator)
        whenever(messageEvent.jda).thenReturn(jda)
        whenever(messageEvent.target).thenReturn(selectedMessage)
        whenever(selectedMessage.member).thenReturn(targetMember)
        whenever(jda.selfUser).thenReturn(selfUser)
        whenever(selfUser.idLong).thenReturn(1L)
        whenever(moderator.hasPermission(Permission.BAN_MEMBERS)).thenReturn(true)
        whenever(targetMember.idLong).thenReturn(1L)
        whenever(messageEvent.reply(any<String>())).thenReturn(replyAction)
        whenever(replyAction.setEphemeral(true)).thenReturn(replyAction)

        command.onMessageContextInteraction(messageEvent)

        verify(messageEvent).reply("You can't ban the bot itself.")
        verify(moderator, never()).canInteract(targetMember)
        verify(messageEvent, never()).deferReply(true)
    }

    @Test
    fun `command data includes user and message context menus`() {
        val commands = command.getCommandsData()

        kotlin.test.assertEquals(2, commands.size)
        kotlin.test.assertEquals(listOf(Command.Type.USER, Command.Type.MESSAGE), commands.map { it.type })
        kotlin.test.assertTrue(commands.all { it.name == "Ban Spam Account" })
    }

    private fun stubSuccessfulBanFlow(embeds: List<MessageEmbed>) {
        stubUserContextMenuStart()
        whenever(targetMember.user).thenReturn(targetUser)
        whenever(targetUser.openPrivateChannel()).thenReturn(openPrivateChannelAction)
        doAnswer {
            val success = it.arguments[0] as Consumer<PrivateChannel>
            success.accept(privateChannel)
            null
        }.whenever(openPrivateChannelAction).queue(any<Consumer<PrivateChannel>>(), any<Consumer<Throwable>>())
        whenever(privateChannel.sendMessageEmbeds(any<MessageEmbed>())).thenReturn(messageCreateAction)
        doAnswer {
            val success = it.arguments[0] as Consumer<Message>
            success.accept(dmMessage)
            null
        }.whenever(messageCreateAction).queue(any<Consumer<Message>>(), any<Consumer<Throwable>>())
        whenever(guild.ban(targetMember, 1, TimeUnit.DAYS)).thenReturn(banRestAction)
        whenever(banRestAction.reason("Compromised account")).thenReturn(banRestAction)
        doAnswer {
            val success = it.arguments[0] as Consumer<Void?>
            success.accept(null)
            null
        }.whenever(banRestAction).queue(any<Consumer<Void?>>(), any<Consumer<Throwable>>())
        whenever(dmMessage.embeds).thenReturn(embeds)
        whenever(hook.editOriginal(any<String>())).thenReturn(editAction)
        whenever(editAction.setEmbeds(any<Collection<MessageEmbed>>())).thenReturn(editAction)
    }

    private fun stubSuccessfulMessageBanFlow(embeds: List<MessageEmbed>) {
        stubMessageContextMenuStart()
        whenever(targetMember.user).thenReturn(targetUser)
        whenever(targetUser.openPrivateChannel()).thenReturn(openPrivateChannelAction)
        doAnswer {
            val success = it.arguments[0] as Consumer<PrivateChannel>
            success.accept(privateChannel)
            null
        }.whenever(openPrivateChannelAction).queue(any<Consumer<PrivateChannel>>(), any<Consumer<Throwable>>())
        whenever(privateChannel.sendMessageEmbeds(any<MessageEmbed>())).thenReturn(messageCreateAction)
        doAnswer {
            val success = it.arguments[0] as Consumer<Message>
            success.accept(dmMessage)
            null
        }.whenever(messageCreateAction).queue(any<Consumer<Message>>(), any<Consumer<Throwable>>())
        whenever(guild.ban(targetMember, 1, TimeUnit.DAYS)).thenReturn(banRestAction)
        whenever(banRestAction.reason("Compromised account")).thenReturn(banRestAction)
        doAnswer {
            val success = it.arguments[0] as Consumer<Void?>
            success.accept(null)
            null
        }.whenever(banRestAction).queue(any<Consumer<Void?>>(), any<Consumer<Throwable>>())
        whenever(dmMessage.embeds).thenReturn(embeds)
        whenever(hook.editOriginal(any<String>())).thenReturn(editAction)
        whenever(editAction.setEmbeds(any<Collection<MessageEmbed>>())).thenReturn(editAction)
    }

    private fun stubUserContextMenuStart() {
        whenever(event.name).thenReturn("Ban Spam Account")
        whenever(event.member).thenReturn(moderator)
        whenever(event.guild).thenReturn(guild)
        whenever(event.jda).thenReturn(jda)
        whenever(event.targetMember).thenReturn(targetMember)
        whenever(jda.selfUser).thenReturn(selfUser)
        whenever(selfUser.idLong).thenReturn(999L)
        whenever(moderator.guild).thenReturn(guild)
        whenever(moderator.hasPermission(Permission.BAN_MEMBERS)).thenReturn(true)
        whenever(targetMember.idLong).thenReturn(1L)
        whenever(moderator.canInteract(targetMember)).thenReturn(true)
        whenever(moderator.user).thenReturn(moderatorUser)
        whenever(moderator.user.effectiveAvatarUrl).thenReturn("https://example.invalid/mod.png")
        whenever(moderatorUser.name).thenReturn("ModeratorUser")
        whenever(targetUser.name).thenReturn("TargetUser")
        whenever(guild.name).thenReturn("Test Guild")
        whenever(guild.idLong).thenReturn(1L)
        whenever(event.deferReply(true)).thenReturn(replyAction)
        doAnswer {
            val success = it.arguments[0] as Consumer<InteractionHook>
            success.accept(hook)
            null
        }.whenever(replyAction).queue(any<Consumer<InteractionHook>>())
    }

    private fun stubMessageContextMenuStart() {
        whenever(messageEvent.name).thenReturn("Ban Spam Account")
        whenever(messageEvent.member).thenReturn(moderator)
        whenever(messageEvent.guild).thenReturn(guild)
        whenever(messageEvent.jda).thenReturn(jda)
        whenever(messageEvent.target).thenReturn(selectedMessage)
        whenever(selectedMessage.member).thenReturn(targetMember)
        whenever(jda.selfUser).thenReturn(selfUser)
        whenever(selfUser.idLong).thenReturn(999L)
        whenever(moderator.guild).thenReturn(guild)
        whenever(moderator.hasPermission(Permission.BAN_MEMBERS)).thenReturn(true)
        whenever(targetMember.idLong).thenReturn(1L)
        whenever(moderator.canInteract(targetMember)).thenReturn(true)
        whenever(moderator.user).thenReturn(moderatorUser)
        whenever(moderator.user.effectiveAvatarUrl).thenReturn("https://example.invalid/mod.png")
        whenever(moderatorUser.name).thenReturn("ModeratorUser")
        whenever(targetUser.name).thenReturn("TargetUser")
        whenever(guild.name).thenReturn("Test Guild")
        whenever(guild.idLong).thenReturn(1L)
        whenever(messageEvent.deferReply(true)).thenReturn(replyAction)
        doAnswer {
            val success = it.arguments[0] as Consumer<InteractionHook>
            success.accept(hook)
            null
        }.whenever(replyAction).queue(any<Consumer<InteractionHook>>())
    }
}
