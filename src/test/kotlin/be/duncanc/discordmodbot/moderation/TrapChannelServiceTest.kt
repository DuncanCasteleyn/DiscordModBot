package be.duncanc.discordmodbot.moderation

import be.duncanc.discordmodbot.logging.GuildLogger
import be.duncanc.discordmodbot.logging.MessageHistory
import be.duncanc.discordmodbot.logging.StoredMessageReference
import be.duncanc.discordmodbot.moderation.persistence.GuildTrapChannelRepository
import be.duncanc.discordmodbot.moderation.persistence.TrapChannelUnban
import be.duncanc.discordmodbot.moderation.persistence.TrapChannelUnbanRepository
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.Member
import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.entities.SelfMember
import net.dv8tion.jda.api.entities.User
import net.dv8tion.jda.api.entities.channel.middleman.GuildMessageChannel
import net.dv8tion.jda.api.entities.channel.unions.MessageChannelUnion
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import net.dv8tion.jda.api.requests.RestAction
import net.dv8tion.jda.api.requests.restaction.AuditableRestAction
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.check
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.OffsetDateTime
import java.util.Optional
import java.util.concurrent.TimeUnit
import java.util.function.Consumer

@ExtendWith(MockitoExtension::class)
class TrapChannelServiceTest {
    @Mock
    private lateinit var guildTrapChannelRepository: GuildTrapChannelRepository

    @Mock
    private lateinit var trapChannelUnbanRepository: TrapChannelUnbanRepository

    @Mock
    private lateinit var messageHistory: MessageHistory

    @Mock
    private lateinit var guildLogger: GuildLogger

    @Mock
    private lateinit var jda: JDA

    @Mock
    private lateinit var event: MessageReceivedEvent

    @Mock
    private lateinit var guild: Guild

    @Mock
    private lateinit var member: Member

    @Mock
    private lateinit var selfMember: SelfMember

    @Mock
    private lateinit var user: User

    @Mock
    private lateinit var message: Message

    @Mock
    private lateinit var channelUnion: MessageChannelUnion

    @Mock
    private lateinit var guildMessageChannel: GuildMessageChannel

    @Mock
    private lateinit var banAction: AuditableRestAction<Void>

    @Mock
    private lateinit var unbanAction: AuditableRestAction<Void>

    @Mock
    private lateinit var deleteAction: RestAction<Void>

    private lateinit var service: TrapChannelService

    @BeforeEach
    fun setUp() {
        service = TrapChannelService(
            guildTrapChannelRepository,
            trapChannelUnbanRepository,
            messageHistory,
            guildLogger,
            jda
        )
    }

    @Test
    fun `message in configured trap channel bans user deletes recent messages and schedules unban`() {
        val cachedMessage = StoredMessageReference(
            messageId = 10L,
            guildId = 1L,
            channelId = 99L,
            userId = 5L,
            content = "previous spam",
            createdAtEpochMillis = OffsetDateTime.now().minusMinutes(10).toInstant().toEpochMilli()
        )
        stubTrapEvent()
        whenever(messageHistory.findRecentMessages(eq(1L), eq(5L), any<OffsetDateTime>())).thenReturn(listOf(cachedMessage))
        whenever(guild.ban(member, 0, TimeUnit.DAYS)).thenReturn(banAction)
        whenever(banAction.reason(any())).thenReturn(banAction)
        whenever(guild.getChannelById(GuildMessageChannel::class.java, 99L)).thenReturn(guildMessageChannel)
        whenever(selfMember.hasPermission(guildMessageChannel, Permission.MESSAGE_MANAGE)).thenReturn(true)
        whenever(guildMessageChannel.deleteMessagesByIds(any<Collection<String>>())).thenReturn(deleteAction)
        doSuccess(banAction)
        doNothingOnQueue(deleteAction)

        service.handleTrapMessage(event)

        verify(guild).ban(member, 0, TimeUnit.DAYS)
        verify(banAction).reason("Triggered the configured spam trap channel")
        verify(guildMessageChannel).deleteMessagesByIds(check {
            kotlin.test.assertEquals(setOf("10", "11"), it.toSet())
        })

        val unbanCaptor = argumentCaptor<TrapChannelUnban>()
        verify(trapChannelUnbanRepository).save(unbanCaptor.capture())
        kotlin.test.assertEquals(1L, unbanCaptor.firstValue.guildId)
        kotlin.test.assertEquals(5L, unbanCaptor.firstValue.userId)

        verify(guildLogger).log(
            any(),
            eq(user),
            eq(guild),
            isNull(),
            eq(GuildLogger.LogTypeAction.MODERATOR),
            isNull()
        )
    }

    @Test
    fun `perform pending unbans removes completed entries`() {
        val scheduledUnban = TrapChannelUnban(1L, 5L, OffsetDateTime.now().minusMinutes(1))
        whenever(trapChannelUnbanRepository.findAllByUnbanAtLessThanEqual(any())).thenReturn(listOf(scheduledUnban))
        whenever(jda.getGuildById(1L)).thenReturn(guild)
        whenever(guild.unban(any())).thenReturn(unbanAction)
        whenever(unbanAction.reason(any())).thenReturn(unbanAction)
        doSuccess(unbanAction)

        service.performPendingUnbans()

        verify(guild).unban(any())
        verify(unbanAction).reason("Automatic trap channel release")
        verify(trapChannelUnbanRepository).delete(scheduledUnban)
        verify(guildLogger).log(
            any(),
            isNull(),
            eq(guild),
            isNull(),
            eq(GuildLogger.LogTypeAction.MODERATOR),
            isNull()
        )
    }

    private fun stubTrapEvent() {
        whenever(event.isFromGuild).thenReturn(true)
        whenever(event.isWebhookMessage).thenReturn(false)
        whenever(event.author).thenReturn(user)
        whenever(user.isBot).thenReturn(false)
        whenever(user.idLong).thenReturn(5L)
        whenever(event.guild).thenReturn(guild)
        whenever(guild.idLong).thenReturn(1L)
        whenever(guild.id).thenReturn("1")
        whenever(guild.selfMember).thenReturn(selfMember)
        whenever(selfMember.hasPermission(Permission.BAN_MEMBERS)).thenReturn(true)
        whenever(selfMember.canInteract(member)).thenReturn(true)
        whenever(event.member).thenReturn(member)
        whenever(member.id).thenReturn("5")
        whenever(member.idLong).thenReturn(5L)
        whenever(member.user).thenReturn(user)
        whenever(member.nickname).thenReturn("spammer")
        whenever(user.name).thenReturn("spammer#0001")
        whenever(event.channel).thenReturn(channelUnion)
        whenever(channelUnion.idLong).thenReturn(99L)
        whenever(channelUnion.asMention).thenReturn("<#99>")
        whenever(event.message).thenReturn(message)
        whenever(event.messageIdLong).thenReturn(11L)
        whenever(message.contentDisplay).thenReturn("trap message")
        whenever(message.timeCreated).thenReturn(OffsetDateTime.now())
        whenever(guildTrapChannelRepository.findById(1L)).thenReturn(Optional.of(be.duncanc.discordmodbot.moderation.persistence.GuildTrapChannel(1L, 99L)))
    }

    private fun doSuccess(action: RestAction<Void>) {
        org.mockito.kotlin.doAnswer {
            val success = it.arguments[0] as Consumer<Void?>
            success.accept(null)
            null
        }.whenever(action).queue(any<Consumer<Void?>>(), any<Consumer<Throwable>>())
    }

    private fun doNothingOnQueue(action: RestAction<Void>) {
        org.mockito.kotlin.doAnswer { null }
            .whenever(action)
            .queue(isNull(), any<Consumer<Throwable>>())
    }
}
