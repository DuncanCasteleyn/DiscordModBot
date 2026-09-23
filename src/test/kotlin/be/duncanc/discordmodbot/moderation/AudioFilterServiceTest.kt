package be.duncanc.discordmodbot.moderation

import be.duncanc.discordmodbot.logging.GuildLogger
import be.duncanc.discordmodbot.moderation.persistence.AudioFilterSettings
import be.duncanc.discordmodbot.moderation.persistence.AudioFilterSettingsRepository
import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.Member
import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.entities.MessageEmbed
import net.dv8tion.jda.api.entities.SelfMember
import net.dv8tion.jda.api.entities.User
import net.dv8tion.jda.api.entities.channel.unions.MessageChannelUnion
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import net.dv8tion.jda.api.requests.restaction.AuditableRestAction
import net.dv8tion.jda.api.requests.restaction.MessageCreateAction
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.*
import java.awt.Color
import java.time.Duration
import java.util.Optional
import java.util.concurrent.TimeUnit
import java.util.function.Consumer
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@ExtendWith(MockitoExtension::class)
class AudioFilterServiceTest {
    @Mock
    private lateinit var audioFilterSettingsRepository: AudioFilterSettingsRepository

    @Mock
    private lateinit var guildLogger: GuildLogger

    @Mock
    private lateinit var event: MessageReceivedEvent

    @Mock
    private lateinit var guild: Guild

    @Mock
    private lateinit var selfMember: SelfMember

    @Mock
    private lateinit var member: Member

    @Mock
    private lateinit var user: User

    @Mock
    private lateinit var message: Message

    @Mock
    private lateinit var warning: Message

    @Mock
    private lateinit var channelUnion: MessageChannelUnion

    @Mock
    private lateinit var attachment: Message.Attachment

    @Mock
    private lateinit var deleteAction: AuditableRestAction<Void>

    @Mock
    private lateinit var timeoutAction: AuditableRestAction<Void>

    @Mock
    private lateinit var warningDeleteAction: AuditableRestAction<Void>

    @Mock
    private lateinit var messageCreateAction: MessageCreateAction

    private lateinit var service: AudioFilterService

    @BeforeEach
    fun setUp() {
        service = AudioFilterService(audioFilterSettingsRepository, guildLogger)
    }

    @Test
    fun `handleMessage ignores bot messages`() {
        whenever(event.isFromGuild).thenReturn(true)
        whenever(event.author).thenReturn(user)
        whenever(user.isBot).thenReturn(true)

        service.handleMessage(event)

        verify(audioFilterSettingsRepository, never()).findById(any<Long>())
    }

    @Test
    fun `handleMessage ignores webhook messages`() {
        whenever(event.isFromGuild).thenReturn(true)
        whenever(event.author).thenReturn(user)
        whenever(user.isBot).thenReturn(false)
        whenever(event.isWebhookMessage).thenReturn(true)

        service.handleMessage(event)

        verify(audioFilterSettingsRepository, never()).findById(any<Long>())
    }

    @Test
    fun `handleMessage does nothing when the filter is disabled`() {
        stubGuildContext()
        whenever(audioFilterSettingsRepository.findById(1L)).thenReturn(Optional.empty())

        service.handleMessage(event)

        verify(message, never()).delete()
        verifyNoInteractions(guildLogger)
    }

    @Test
    fun `handleMessage does nothing when the bot lacks manage message permission`() {
        stubEnabledFilterMessage(enabledSettings)
        whenever(selfMember.hasPermission(Permission.MESSAGE_MANAGE)).thenReturn(false)

        service.handleMessage(event)

        verify(message, never()).delete()
    }

    @Test
    fun `handleMessage ignores administrators`() {
        stubEnabledFilterMessage(enabledSettings)
        whenever(member.hasPermission(Permission.ADMINISTRATOR)).thenReturn(true)

        service.handleMessage(event)

        verify(message, never()).delete()
        verifyNoInteractions(guildLogger)
    }

    @Test
    fun `handleMessage ignores members the bot cannot interact with`() {
        stubEnabledFilterMessage(enabledSettings)
        whenever(member.hasPermission(Permission.ADMINISTRATOR)).thenReturn(false)
        whenever(selfMember.canInteract(member)).thenReturn(false)

        service.handleMessage(event)

        verify(message, never()).delete()
        verifyNoInteractions(guildLogger)
    }

    @Test
    fun `handleMessage ignores attachments that are not audio files`() {
        stubEnabledFilterMessage(enabledSettings)
        stubDeletableMember()
        whenever(attachment.contentType).thenReturn("image/png")

        service.handleMessage(event)

        verify(message, never()).delete()
        verifyNoInteractions(guildLogger)
    }

    @Test
    fun `handleMessage ignores text files with octet stream content type`() {
        stubEnabledFilterMessage(enabledSettings)
        stubDeletableMember()
        whenever(attachment.contentType).thenReturn("application/octet-stream")
        whenever(attachment.fileExtension).thenReturn("txt")

        service.handleMessage(event)

        verify(message, never()).delete()
        verifyNoInteractions(guildLogger)
    }

    @Test
    fun `handleMessage deletes message with audio content type and applies timeout`() {
        stubEnabledFilterMessage(AudioFilterSettings(1L, 60L))
        stubDeletableMember()
        whenever(attachment.contentType).thenReturn("audio/mpeg")
        stubSuccessfulDeletion()
        stubTimeout(Duration.ofMinutes(60L))

        service.handleMessage(event)

        verify(message).delete()
        verify(deleteAction).reason("Posted a blocked audio file")
        verify(member).timeoutFor(Duration.ofMinutes(60L))
        verify(timeoutAction).reason("Posted a blocked audio file")
        verify(guildLogger).log(
            any(),
            eq(user),
            eq(guild),
            isNull(),
            eq(GuildLogger.LogTypeAction.MODERATOR),
            isNull()
        )
        val embedCaptor = argumentCaptor<MessageEmbed>()
        verify(channelUnion).sendMessageEmbeds(embedCaptor.capture())
        val embed = embedCaptor.firstValue
        assertEquals(Color.RED, embed.color)
        assertEquals("Audio file removed", embed.title)
        assertTrue(embed.description!!.contains("<@5>"))
        verify(warningDeleteAction).queueAfter(30L, TimeUnit.SECONDS)
    }

    @Test
    fun `handleMessage detects audio by extension for octet stream content type`() {
        stubEnabledFilterMessage(enabledSettings)
        stubDeletableMember()
        whenever(attachment.contentType).thenReturn("application/octet-stream")
        whenever(attachment.fileExtension).thenReturn("mp3")
        stubSuccessfulDeletion()

        service.handleMessage(event)

        verify(message).delete()
        verify(member, never()).timeoutFor(any<Duration>())
    }

    @Test
    fun `handleMessage detects audio by extension case insensitively when content type is unknown`() {
        stubEnabledFilterMessage(enabledSettings)
        stubDeletableMember()
        whenever(attachment.contentType).thenReturn(null)
        whenever(attachment.fileExtension).thenReturn("MP3")
        stubSuccessfulDeletion()

        service.handleMessage(event)

        verify(message).delete()
    }

    @Test
    fun `handleMessage skips timeout when no timeout is configured`() {
        stubEnabledFilterMessage(enabledSettings)
        stubDeletableMember()
        whenever(attachment.contentType).thenReturn("audio/mpeg")
        stubSuccessfulDeletion()

        service.handleMessage(event)

        verify(message).delete()
        verify(member, never()).timeoutFor(any<Duration>())
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
    fun `handleMessage does not timeout or warn when deletion fails`() {
        stubEnabledFilterMessage(AudioFilterSettings(1L, 60L))
        stubDeletableMember()
        whenever(attachment.contentType).thenReturn("audio/mpeg")
        whenever(attachment.fileName).thenReturn("song.mp3")
        whenever(message.delete()).thenReturn(deleteAction)
        whenever(deleteAction.reason(any())).thenReturn(deleteAction)
        whenever(member.id).thenReturn("5")
        whenever(guild.id).thenReturn("1")
        doAnswer { invocation ->
            invocation.component2<Consumer<Throwable>>().accept(RuntimeException("discord unavailable"))
            null
        }.whenever(deleteAction).queue(any(), any())

        service.handleMessage(event)

        verify(member, never()).timeoutFor(any<Duration>())
        verify(channelUnion, never()).sendMessageEmbeds(any<MessageEmbed>())
        verifyNoInteractions(guildLogger)
    }

    @Test
    fun `enableFilter saves new settings`() {
        whenever(audioFilterSettingsRepository.save(AudioFilterSettings(1L, 30L)))
            .thenReturn(AudioFilterSettings(1L, 30L))

        val result = service.enableFilter(1L, 30L)

        assertEquals(AudioFilterSettings(1L, 30L), result)
    }

    @Test
    fun `setTimeout returns null when the filter is disabled`() {
        whenever(audioFilterSettingsRepository.findById(1L)).thenReturn(Optional.empty())

        val result = service.setTimeout(1L, 30L)

        assertNull(result)
        verify(audioFilterSettingsRepository, never()).save(any())
    }

    @Test
    fun `setTimeout updates existing settings`() {
        whenever(audioFilterSettingsRepository.findById(1L)).thenReturn(Optional.of(AudioFilterSettings(1L, 15L)))
        whenever(audioFilterSettingsRepository.save(AudioFilterSettings(1L, 45L)))
            .thenReturn(AudioFilterSettings(1L, 45L))

        val result = service.setTimeout(1L, 45L)

        assertEquals(AudioFilterSettings(1L, 45L), result)
    }

    @Test
    fun `disableFilter deletes settings row`() {
        service.disableFilter(1L)

        verify(audioFilterSettingsRepository).deleteById(1L)
    }

    private val enabledSettings: AudioFilterSettings
        get() = AudioFilterSettings(1L, null)

    private fun stubGuildContext() {
        whenever(event.isFromGuild).thenReturn(true)
        whenever(event.author).thenReturn(user)
        whenever(user.isBot).thenReturn(false)
        whenever(event.guild).thenReturn(guild)
        whenever(guild.idLong).thenReturn(1L)
    }

    private fun stubEnabledFilterMessage(settings: AudioFilterSettings) {
        stubGuildContext()
        whenever(audioFilterSettingsRepository.findById(1L)).thenReturn(Optional.of(settings))
        whenever(event.member).thenReturn(member)
        whenever(guild.selfMember).thenReturn(selfMember)
        whenever(selfMember.hasPermission(Permission.MESSAGE_MANAGE)).thenReturn(true)
    }

    private fun stubDeletableMember() {
        whenever(member.hasPermission(Permission.ADMINISTRATOR)).thenReturn(false)
        whenever(selfMember.canInteract(member)).thenReturn(true)
        whenever(event.message).thenReturn(message)
        whenever(message.attachments).thenReturn(listOf(attachment))
    }

    private fun stubSuccessfulDeletion() {
        whenever(member.user).thenReturn(user)
        whenever(member.nickname).thenReturn("Nicky")
        whenever(user.name).thenReturn("tester")
        whenever(event.channel).thenReturn(channelUnion)
        whenever(channelUnion.asMention).thenReturn("<#99>")
        whenever(attachment.fileName).thenReturn("song.mp3")
        whenever(message.delete()).thenReturn(deleteAction)
        whenever(deleteAction.reason(any())).thenReturn(deleteAction)
        doAnswer { invocation ->
            invocation.component1<Consumer<Void?>>().accept(null)
            null
        }.whenever(deleteAction).queue(any(), any())
        whenever(member.asMention).thenReturn("<@5>")
        whenever(user.effectiveAvatarUrl).thenReturn("https://cdn.discordapp.com/avatars/5/avatar.png")
        whenever(channelUnion.sendMessageEmbeds(any<MessageEmbed>())).thenReturn(messageCreateAction)
        doAnswer { invocation ->
            invocation.component1<Consumer<Message>>().accept(warning)
            null
        }.whenever(messageCreateAction).queue(any(), any())
        whenever(warning.delete()).thenReturn(warningDeleteAction)
    }

    private fun stubTimeout(duration: Duration) {
        whenever(selfMember.hasPermission(Permission.MODERATE_MEMBERS)).thenReturn(true)
        whenever(member.timeoutFor(duration)).thenReturn(timeoutAction)
        whenever(timeoutAction.reason(any())).thenReturn(timeoutAction)
    }
}
