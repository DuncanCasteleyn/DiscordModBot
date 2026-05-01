package be.duncanc.discordmodbot.moderation

import be.duncanc.discordmodbot.logging.GuildLogger
import be.duncanc.discordmodbot.moderation.persistence.GuildTrapChannelRepository
import be.duncanc.discordmodbot.moderation.persistence.TrapChannelUnban
import be.duncanc.discordmodbot.moderation.persistence.TrapChannelUnbanRepository
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.entities.*
import net.dv8tion.jda.api.entities.channel.unions.MessageChannelUnion
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import net.dv8tion.jda.api.requests.RestAction
import net.dv8tion.jda.api.requests.restaction.AuditableRestAction
import net.dv8tion.jda.api.requests.restaction.MessageCreateAction
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.*
import java.time.OffsetDateTime
import java.util.*
import java.util.concurrent.TimeUnit
import java.util.function.Consumer

@ExtendWith(MockitoExtension::class)
class TrapChannelServiceTest {
    @Mock
    private lateinit var guildTrapChannelRepository: GuildTrapChannelRepository

    @Mock
    private lateinit var trapChannelUnbanRepository: TrapChannelUnbanRepository

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
    private lateinit var channelUnion: MessageChannelUnion

    @Mock
    private lateinit var banAction: AuditableRestAction<Void>

    @Mock
    private lateinit var messageCreateAction: MessageCreateAction

    @Mock
    private lateinit var unbanAction: AuditableRestAction<Void>

    private lateinit var service: TrapChannelService

    @BeforeEach
    fun setUp() {
        service = TrapChannelService(
            guildTrapChannelRepository,
            trapChannelUnbanRepository,
            guildLogger,
            jda
        )
    }

    @Test
    fun `message in configured trap channel bans user and schedules unban`() {
        stubTrapEvent()
        whenever(guild.ban(member, 10, TimeUnit.MINUTES)).thenReturn(banAction)
        whenever(banAction.reason(any())).thenReturn(banAction)
        whenever(channelUnion.sendMessage(any<String>())).thenReturn(messageCreateAction)
        doSuccess(banAction)

        service.handleTrapMessage(event)

        verify(guild).ban(member, 10, TimeUnit.MINUTES)
        verify(banAction).reason("Triggered the configured spam trap channel")

        val unbanCaptor = argumentCaptor<TrapChannelUnban>()
        verify(trapChannelUnbanRepository).save(unbanCaptor.capture())
        kotlin.test.assertEquals(1L, unbanCaptor.firstValue.guildId)
        kotlin.test.assertEquals(5L, unbanCaptor.firstValue.userId)

        verify(channelUnion).sendMessage(
            "<@5> was automatically banned for posting in this channel. This channel is a spambot trap. Do not post here."
        )
        verify(messageCreateAction).queue()

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
    fun `perform pending unbans keeps overdue entries when guild is unavailable`() {
        val scheduledUnban = TrapChannelUnban(1L, 5L, OffsetDateTime.now().minusMinutes(1))
        whenever(trapChannelUnbanRepository.findAllByUnbanAtLessThanEqual(any())).thenReturn(listOf(scheduledUnban))
        whenever(jda.getGuildById(1L)).thenReturn(null)

        service.performPendingUnbans()

        verify(jda).getGuildById(1L)
        verify(trapChannelUnbanRepository, never()).delete(scheduledUnban)
        verify(guild, never()).unban(any())
        verifyNoInteractions(guildLogger)
    }

    @Test
    fun `perform pending unbans removes completed entries`() {
        val scheduledUnban = TrapChannelUnban(1L, 5L, OffsetDateTime.now().minusMinutes(1))
        whenever(trapChannelUnbanRepository.findAllByUnbanAtLessThanEqual(any())).thenReturn(listOf(scheduledUnban))
        whenever(jda.getGuildById(1L)).thenReturn(guild)
        whenever(guild.unban(any<UserSnowflake>())).thenReturn(unbanAction)
        whenever(unbanAction.reason(any())).thenReturn(unbanAction)

        service.performPendingUnbans()

        verify(guild).unban(any<UserSnowflake>())
        verify(unbanAction).reason("Automatic trap channel unban")
        val queueCaptor = argumentCaptor<Consumer<Void?>>()
        verify(unbanAction).queue(queueCaptor.capture(), any())
        queueCaptor.firstValue.accept(null)
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
        whenever(guildTrapChannelRepository.findById(1L)).thenReturn(Optional.of(be.duncanc.discordmodbot.moderation.persistence.GuildTrapChannel(1L, 99L)))
    }

    private fun doSuccess(action: RestAction<Void>) {
        doAnswer {
            @Suppress("UNCHECKED_CAST")
            val success = it.getArgument<Consumer<Void?>>(0)
            success.accept(null)
            null
        }.whenever(action).queue(any(), any())
    }
}
