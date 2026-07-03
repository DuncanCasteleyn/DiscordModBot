package be.duncanc.discordmodbot.reddit

import be.duncanc.discordmodbot.reddit.persistence.RedditAlertSettings
import be.duncanc.discordmodbot.reddit.persistence.RedditAlertSettingsRepository
import be.duncanc.discordmodbot.reddit.persistence.RedditPendingPost
import be.duncanc.discordmodbot.reddit.persistence.RedditPendingPostRepository
import be.duncanc.discordmodbot.reddit.persistence.RedditPostMirror
import be.duncanc.discordmodbot.reddit.persistence.RedditPostMirrorRepository
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.entities.MessageEmbed
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel
import net.dv8tion.jda.api.exceptions.ErrorResponseException
import net.dv8tion.jda.api.requests.ErrorResponse
import net.dv8tion.jda.api.requests.restaction.AuditableRestAction
import net.dv8tion.jda.api.requests.restaction.MessageCreateAction
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.Instant
import java.util.Optional
import java.util.function.Consumer

@ExtendWith(MockitoExtension::class)
class RedditPollingServiceTest {
    @Mock
    private lateinit var redditRssClient: RedditRssClient

    @Mock
    private lateinit var redditAlertSettingsRepository: RedditAlertSettingsRepository

    @Mock
    private lateinit var redditPostMirrorRepository: RedditPostMirrorRepository

    @Mock
    private lateinit var redditPendingPostRepository: RedditPendingPostRepository

    @Mock
    private lateinit var jda: JDA

    @Mock
    private lateinit var textChannel: TextChannel

    @Mock
    private lateinit var messageCreateAction: MessageCreateAction

    @Mock
    private lateinit var deleteAction: AuditableRestAction<Void?>

    @Mock
    private lateinit var message: Message

    private lateinit var service: RedditPollingService


    @BeforeEach
    fun setUp() {
        service = RedditPollingService(
            redditRssClient = redditRssClient,
            redditAlertSettingsRepository = redditAlertSettingsRepository,
            redditPostMirrorRepository = redditPostMirrorRepository,
            redditPendingPostRepository = redditPendingPostRepository,
            jda = jda
        )
    }

    @Test
    fun `baseline stores current rss posts without discord message ids`() {
        whenever(redditRssClient.fetchNewestPosts("Re_Zero")).thenReturn(listOf(post("t3_first")))
        whenever(redditPostMirrorRepository.findById("1:t3_first")).thenReturn(Optional.empty())
        whenever(redditPostMirrorRepository.save(any<RedditPostMirror>())).thenAnswer { it.arguments[0] }

        service.baselineCurrentPosts(1L, "Re_Zero")

        val mirrorCaptor = argumentCaptor<RedditPostMirror>()
        verify(redditPostMirrorRepository).save(mirrorCaptor.capture())
        assertEquals("1:t3_first", mirrorCaptor.firstValue.id)
        assertEquals(null, mirrorCaptor.firstValue.discordChannelId)
        assertEquals(null, mirrorCaptor.firstValue.discordMessageId)
    }

    @Test
    fun `poll mirrors new rss post and stores discord message id`() {
        whenever(redditRssClient.fetchNewestPosts("Re_Zero")).thenReturn(listOf(post("t3_new")))
        whenever(redditAlertSettingsRepository.findAll()).thenReturn(listOf(RedditAlertSettings(1L, 11L)))
        whenever(jda.getTextChannelById(11L)).thenReturn(textChannel)
        whenever(redditPostMirrorRepository.findAll()).thenReturn(emptyList())
        whenever(redditPostMirrorRepository.findById("1:t3_new")).thenReturn(Optional.empty())
        whenever(redditPendingPostRepository.existsById("1:t3_new")).thenReturn(false)
        whenever(redditPendingPostRepository.save(any<RedditPendingPost>())).thenAnswer { it.arguments[0] }
        whenever(redditPostMirrorRepository.save(any<RedditPostMirror>())).thenAnswer { it.arguments[0] }
        whenever(textChannel.idLong).thenReturn(11L)
        whenever(textChannel.sendMessageEmbeds(any<MessageEmbed>())).thenReturn(messageCreateAction)
        whenever(message.idLong).thenReturn(101L)
        doAnswer { invocation ->
            invocation.component1<Consumer<Message>>().accept(message)
            null
        }.whenever(messageCreateAction).queue(any(), any())

        service.pollSubreddit()

        val mirrorCaptor = argumentCaptor<RedditPostMirror>()
        verify(redditPostMirrorRepository).save(mirrorCaptor.capture())
        assertEquals("1:t3_new", mirrorCaptor.lastValue.id)
        assertEquals(11L, mirrorCaptor.lastValue.discordChannelId)
        assertEquals(101L, mirrorCaptor.lastValue.discordMessageId)
        verify(redditPendingPostRepository).deleteById("1:t3_new")
    }

    @Test
    fun `baseline throws when rss client fails`() {
        whenever(redditRssClient.fetchNewestPosts("Re_Zero")).thenThrow(RuntimeException("network error"))

        assertThrows<IllegalStateException> { service.baselineCurrentPosts(1L, "Re_Zero") }
    }

    @Test
    fun `poll disables channel on terminal send failure`() {
        val settings = RedditAlertSettings(1L, 11L)
        whenever(redditRssClient.fetchNewestPosts("Re_Zero")).thenReturn(listOf(post("t3_new")))
        whenever(redditAlertSettingsRepository.findAll()).thenReturn(listOf(settings))
        whenever(jda.getTextChannelById(11L)).thenReturn(textChannel)
        whenever(redditPostMirrorRepository.findAll()).thenReturn(emptyList())
        whenever(redditPostMirrorRepository.findById("1:t3_new")).thenReturn(Optional.empty())
        whenever(redditPendingPostRepository.existsById("1:t3_new")).thenReturn(false)
        whenever(redditPendingPostRepository.save(any<RedditPendingPost>())).thenAnswer { it.arguments[0] }
        whenever(textChannel.idLong).thenReturn(11L)
        whenever(textChannel.sendMessageEmbeds(any<MessageEmbed>())).thenReturn(messageCreateAction)
        val exception = mock<ErrorResponseException>()
        whenever(exception.errorResponse).thenReturn(ErrorResponse.MISSING_PERMISSIONS)
        doAnswer { invocation ->
            invocation.component2<Consumer<Throwable>>().accept(exception)
            null
        }.whenever(messageCreateAction).queue(any(), any())

        service.pollSubreddit()

        val settingsCaptor = argumentCaptor<RedditAlertSettings>()
        verify(redditAlertSettingsRepository).save(settingsCaptor.capture())
        assertEquals(null, settingsCaptor.firstValue.channelId)
        verify(redditPostMirrorRepository, never()).save(any<RedditPostMirror>())
        verify(redditPendingPostRepository).deleteById("1:t3_new")
    }

    @Test
    fun `poll deletes mirrored message when tracked post is missing inside rss window`() {
        val trackedMirror = RedditPostMirror(
            id = "1:t3_removed",
            guildId = 1L,
            redditPostId = "t3_removed",
            discordChannelId = 11L,
            discordMessageId = 101L,
            publishedAt = Instant.parse("2026-07-02T12:00:00Z"),
            permalink = "https://www.reddit.com/r/Re_Zero/comments/removed/title/"
        )
        whenever(redditRssClient.fetchNewestPosts("Re_Zero")).thenReturn(
            listOf(
                post("t3_newer", publishedAt = Instant.parse("2026-07-02T13:00:00Z")),
                post("t3_older", publishedAt = Instant.parse("2026-07-02T11:00:00Z"))
            )
        )
        whenever(redditAlertSettingsRepository.findAll()).thenReturn(listOf(RedditAlertSettings(1L, 11L)))
        whenever(jda.getTextChannelById(11L)).thenReturn(textChannel)
        whenever(redditPostMirrorRepository.findAll()).thenReturn(listOf(trackedMirror))
        whenever(redditPostMirrorRepository.findById("1:t3_older")).thenReturn(
            Optional.of(
                trackedMirror.copy(
                    id = "1:t3_older",
                    redditPostId = "t3_older",
                    discordMessageId = 102L,
                    publishedAt = Instant.parse("2026-07-02T11:00:00Z")
                )
            )
        )
        whenever(redditPostMirrorRepository.findById("1:t3_newer")).thenReturn(
            Optional.of(
                trackedMirror.copy(
                    id = "1:t3_newer",
                    redditPostId = "t3_newer",
                    discordMessageId = 103L,
                    publishedAt = Instant.parse("2026-07-02T13:00:00Z")
                )
            )
        )
        whenever(redditPostMirrorRepository.save(any<RedditPostMirror>())).thenAnswer { it.arguments[0] }
        whenever(textChannel.deleteMessageById(101L)).thenReturn(deleteAction)
        doAnswer { invocation ->
            invocation.component1<Consumer<Void?>>().accept(null)
            null
        }.whenever(deleteAction).queue(any(), any())

        service.pollSubreddit()

        val mirrorCaptor = argumentCaptor<RedditPostMirror>()
        verify(redditPostMirrorRepository, times(3)).save(mirrorCaptor.capture())
        assertEquals(true, mirrorCaptor.allValues.first { it.redditPostId == "t3_removed" }.deleted)
        verify(textChannel, never()).sendMessageEmbeds(any<MessageEmbed>())
    }

    private fun post(
        id: String,
        publishedAt: Instant = Instant.parse("2026-07-02T12:00:00Z")
    ): RedditPost {
        return RedditPost(
            id = id,
            title = "Test post $id",
            author = "Subaru",
            permalink = "https://www.reddit.com/r/Re_Zero/comments/$id/title/",
            publishedAt = publishedAt,
            thumbnailUrl = null
        )
    }
}
