package be.duncanc.discordmodbot.reddit

import be.duncanc.discordmodbot.reddit.persistence.RedditAlertSettings
import be.duncanc.discordmodbot.reddit.persistence.RedditAlertSettingsRepository
import be.duncanc.discordmodbot.reddit.persistence.RedditPendingPost
import be.duncanc.discordmodbot.reddit.persistence.RedditPendingPostRepository
import be.duncanc.discordmodbot.reddit.persistence.RedditPostMirror
import be.duncanc.discordmodbot.reddit.persistence.RedditPostMirrorRepository
import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.Member
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
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.Duration
import java.time.Instant
import java.util.Optional

@ExtendWith(MockitoExtension::class)
class RedditConfigCommandTest {
    @Mock
    private lateinit var redditAlertSettingsRepository: RedditAlertSettingsRepository

    @Mock
    private lateinit var redditPostMirrorRepository: RedditPostMirrorRepository

    @Mock
    private lateinit var redditPendingPostRepository: RedditPendingPostRepository

    @Mock
    private lateinit var redditPollingService: RedditPollingService

    @Mock
    private lateinit var slashEvent: SlashCommandInteractionEvent

    @Mock
    private lateinit var guild: Guild

    @Mock
    private lateinit var member: Member

    @Mock
    private lateinit var textChannel: TextChannel

    @Mock
    private lateinit var replyAction: ReplyCallbackAction

    private lateinit var command: TestRedditConfigCommand

    @BeforeEach
    fun setUp() {
        command = TestRedditConfigCommand(
            redditAlertSettingsRepository = redditAlertSettingsRepository,
            redditPostMirrorRepository = redditPostMirrorRepository,
            redditPendingPostRepository = redditPendingPostRepository,
            redditPollingService = redditPollingService,
            redditProperties = RedditProperties(
                subreddit = "Re_Zero",
                pollCron = "0 */2 * * * *",
                readTimeout = Duration.ofSeconds(10),
                userAgent = "test"
            )
        )
    }

    @Test
    fun `set channel stores selected channel and baselines configured subreddit`() {
        stubAuthorizedSlashCommand("set-channel")
        whenever(textChannel.idLong).thenReturn(11L)
        whenever(textChannel.asMention).thenReturn("<#11>")
        whenever(redditAlertSettingsRepository.findById(1L)).thenReturn(
            Optional.of(RedditAlertSettings(guildId = 1L, subreddit = "Anime"))
        )
        command.selectedChannel = textChannel

        command.onSlashCommandInteraction(slashEvent)

        val settingsCaptor = argumentCaptor<RedditAlertSettings>()
        verify(redditAlertSettingsRepository).save(settingsCaptor.capture())
        assertEquals(11L, settingsCaptor.firstValue.channelId)
        assertEquals("Anime", settingsCaptor.firstValue.subreddit)
        verify(redditPollingService).baselineCurrentPosts(1L, "Anime")
        verify(slashEvent).reply("Reddit posts from r/Anime will be mirrored to <#11>.")
    }

    @Test
    fun `set subreddit stores subreddit clears tracked posts and baselines`() {
        stubAuthorizedSlashCommand("set-subreddit")
        val mirror = RedditPostMirror(
            id = "1:t3_old",
            guildId = 1L,
            redditPostId = "t3_old",
            discordChannelId = 11L,
            discordMessageId = 101L,
            publishedAt = Instant.parse("2026-07-02T12:00:00Z"),
            permalink = "https://www.reddit.com/r/Re_Zero/comments/old/title/"
        )
        val pending = RedditPendingPost("1:t3_pending")
        whenever(redditAlertSettingsRepository.findById(1L)).thenReturn(
            Optional.of(RedditAlertSettings(guildId = 1L, channelId = 11L, subreddit = "Re_Zero"))
        )
        whenever(redditPostMirrorRepository.findAll()).thenReturn(listOf(mirror))
        whenever(redditPendingPostRepository.findAll()).thenReturn(listOf(pending))
        whenever(redditPollingService.baselineCurrentPosts(1L, "Anime")).thenReturn(
            listOf(
                RedditPost(
                    id = "t3_new",
                    title = "New",
                    author = "Subaru",
                    permalink = "https://www.reddit.com/r/Anime/comments/t3_new/title/",
                    publishedAt = Instant.parse("2026-07-02T12:00:00Z"),
                    thumbnailUrl = null
                )
            )
        )
        command.subreddit = "Anime"

        command.onSlashCommandInteraction(slashEvent)

        val settingsCaptor = argumentCaptor<RedditAlertSettings>()
        verify(redditAlertSettingsRepository).save(settingsCaptor.capture())
        assertEquals("Anime", settingsCaptor.firstValue.subreddit)
        verify(redditPostMirrorRepository).delete(mirror)
        verify(redditPendingPostRepository).delete(pending)
        verify(redditPollingService).baselineCurrentPosts(1L, "Anime")
        verify(slashEvent).reply("Reddit post mirroring now watches r/Anime.")
    }

    @Test
    fun `set channel does not save settings when baseline fails`() {
        stubAuthorizedSlashCommand("set-channel")
        whenever(redditAlertSettingsRepository.findById(1L)).thenReturn(
            Optional.of(RedditAlertSettings(guildId = 1L, subreddit = "Anime"))
        )
        whenever(redditPollingService.baselineCurrentPosts(1L, "Anime")).thenThrow(
            IllegalStateException("Reddit unavailable")
        )
        command.selectedChannel = textChannel

        command.onSlashCommandInteraction(slashEvent)

        verify(redditAlertSettingsRepository, never()).save(any<RedditAlertSettings>())
        verify(slashEvent).reply("Reddit unavailable")
    }

    @Test
    fun `set subreddit does not save settings or clear tracked posts when baseline fails`() {
        stubAuthorizedSlashCommand("set-subreddit")
        whenever(redditAlertSettingsRepository.findById(1L)).thenReturn(
            Optional.of(RedditAlertSettings(guildId = 1L, channelId = 11L, subreddit = "Re_Zero"))
        )
        whenever(redditPollingService.baselineCurrentPosts(1L, "Anime")).thenThrow(
            IllegalStateException("Reddit unavailable")
        )
        command.subreddit = "Anime"

        command.onSlashCommandInteraction(slashEvent)

        verify(redditAlertSettingsRepository, never()).save(any<RedditAlertSettings>())
        verify(redditPostMirrorRepository, never()).delete(any<RedditPostMirror>())
        verify(redditPendingPostRepository, never()).delete(any<RedditPendingPost>())
        verify(slashEvent).reply("Reddit unavailable")
    }

    @Test
    fun `disable wipes settings and tracked posts`() {
        stubAuthorizedSlashCommand("disable")
        val mirror = RedditPostMirror(
            id = "1:t3_old",
            guildId = 1L,
            redditPostId = "t3_old",
            discordChannelId = 11L,
            discordMessageId = 101L,
            publishedAt = Instant.parse("2026-07-02T12:00:00Z"),
            permalink = "https://www.reddit.com/r/Re_Zero/comments/old/title/"
        )
        whenever(redditPostMirrorRepository.findAll()).thenReturn(listOf(mirror))
        whenever(redditPendingPostRepository.findAll()).thenReturn(emptyList())

        command.onSlashCommandInteraction(slashEvent)

        verify(redditAlertSettingsRepository).deleteById(1L)
        verify(redditPostMirrorRepository).delete(mirror)
        verify(slashEvent).reply("Reddit post mirroring disabled.")
    }

    @Test
    fun `show displays configured subreddit`() {
        stubAuthorizedSlashCommand("show")
        whenever(guild.name).thenReturn("Test Guild")
        whenever(guild.getTextChannelById(11L)).thenReturn(textChannel)
        whenever(textChannel.asMention).thenReturn("<#11>")
        whenever(redditAlertSettingsRepository.findById(1L)).thenReturn(
            Optional.of(RedditAlertSettings(guildId = 1L, channelId = 11L, subreddit = "Anime"))
        )

        command.onSlashCommandInteraction(slashEvent)

        val replyCaptor = argumentCaptor<String>()
        verify(slashEvent).reply(replyCaptor.capture())
        assertEquals(true, replyCaptor.firstValue.contains("- Subreddit: r/Anime"))
        assertEquals(true, replyCaptor.firstValue.contains("- Mirror channel: <#11>"))
    }

    @Test
    fun `command data exposes expected subcommands`() {
        val commandData = command.getCommandsData().single()

        assertEquals("reddit", commandData.name)
        assertEquals(setOf(InteractionContextType.GUILD), commandData.contexts)
        assertEquals(
            listOf("show", "set-channel", "set-subreddit", "disable"),
            commandData.subcommands.map(SubcommandData::getName)
        )
    }

    private fun stubSlashCommandContext() {
        whenever(slashEvent.name).thenReturn("reddit")
        whenever(slashEvent.guild).thenReturn(guild)
        whenever(slashEvent.member).thenReturn(member)
        whenever(slashEvent.reply(any<String>())).thenReturn(replyAction)
        whenever(replyAction.setEphemeral(true)).thenReturn(replyAction)
    }

    private fun stubAuthorizedSlashCommand(subcommandName: String) {
        stubSlashCommandContext()
        whenever(guild.idLong).thenReturn(1L)
        whenever(member.hasPermission(Permission.MANAGE_CHANNEL)).thenReturn(true)
        whenever(slashEvent.subcommandName).thenReturn(subcommandName)
    }

    private class TestRedditConfigCommand(
        redditAlertSettingsRepository: RedditAlertSettingsRepository,
        redditPostMirrorRepository: RedditPostMirrorRepository,
        redditPendingPostRepository: RedditPendingPostRepository,
        redditPollingService: RedditPollingService,
        redditProperties: RedditProperties
    ) : RedditConfigCommand(
        redditAlertSettingsRepository,
        redditPostMirrorRepository,
        redditPendingPostRepository,
        redditPollingService,
        redditProperties
    ) {
        var selectedChannel: TextChannel? = null
        var subreddit: String? = null

        override fun getRequiredTextChannel(event: SlashCommandInteractionEvent): TextChannel? {
            return selectedChannel ?: super.getRequiredTextChannel(event)
        }

        override fun getRequiredSubreddit(event: SlashCommandInteractionEvent): String? {
            return subreddit ?: super.getRequiredSubreddit(event)
        }
    }
}
