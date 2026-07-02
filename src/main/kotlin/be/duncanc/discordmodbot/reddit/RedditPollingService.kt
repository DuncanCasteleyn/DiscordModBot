package be.duncanc.discordmodbot.reddit

import be.duncanc.discordmodbot.reddit.persistence.RedditAlertSettings
import be.duncanc.discordmodbot.reddit.persistence.RedditAlertSettingsRepository
import be.duncanc.discordmodbot.reddit.persistence.RedditPendingPost
import be.duncanc.discordmodbot.reddit.persistence.RedditPendingPostRepository
import be.duncanc.discordmodbot.reddit.persistence.RedditPostMirror
import be.duncanc.discordmodbot.reddit.persistence.RedditPostMirrorRepository
import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.entities.MessageEmbed
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel
import net.dv8tion.jda.api.exceptions.ErrorResponseException
import net.dv8tion.jda.api.requests.ErrorResponse
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import java.awt.Color
import java.time.Instant

@Service
class RedditPollingService(
    private val redditRssClient: RedditRssClient,
    private val redditAlertSettingsRepository: RedditAlertSettingsRepository,
    private val redditPostMirrorRepository: RedditPostMirrorRepository,
    private val redditPendingPostRepository: RedditPendingPostRepository,
    private val jda: JDA
) {
    companion object {
        private val LOG = LoggerFactory.getLogger(RedditPollingService::class.java)
    }

    @Scheduled(cron = $$"${discord-mod-bot.reddit.poll-cron:0 */2 * * * *}")
    fun pollSubreddit() {
        val settings = redditAlertSettingsRepository.findAll().filter { it.channelId != null }
        if (settings.isEmpty()) {
            return
        }

        settings.groupBy { it.subreddit }.forEach { (subreddit, subredditSettings) ->
            val posts = try {
                redditRssClient.fetchNewestPosts(subreddit)
            } catch (exception: Exception) {
                LOG.warn("Failed to poll Reddit RSS for r/{}", subreddit, exception)
                return@forEach
            }
            if (posts.isEmpty()) {
                return@forEach
            }

            subredditSettings.forEach { processGuild(it, posts) }
        }
    }

    fun baselineCurrentPosts(guildId: Long, subreddit: String) {
        val posts = try {
            redditRssClient.fetchNewestPosts(subreddit)
        } catch (exception: Exception) {
            LOG.warn("Failed to baseline Reddit RSS posts for r/{} in guild {}", subreddit, guildId, exception)
            return
        }

        posts.forEach { post ->
            val mirrorId = RedditPostMirror.id(guildId, post.id)
            val existingMirror = redditPostMirrorRepository.findById(mirrorId).orElse(null)
            if (existingMirror == null) {
                redditPostMirrorRepository.save(
                    RedditPostMirror(
                        id = mirrorId,
                        guildId = guildId,
                        redditPostId = post.id,
                        discordChannelId = null,
                        discordMessageId = null,
                        publishedAt = post.publishedAt,
                        permalink = post.permalink
                    )
                )
            } else {
                redditPostMirrorRepository.save(existingMirror)
            }
        }
    }

    private fun processGuild(settings: RedditAlertSettings, posts: List<RedditPost>) {
        val channelId = settings.channelId ?: return
        val channel = jda.getTextChannelById(channelId)
        if (channel == null) {
            disableMissingChannel(settings, channelId)
            return
        }

        cleanupRemovedPosts(settings.guildId, posts)
        posts.sortedBy { it.publishedAt }.forEach { post -> mirrorPost(settings.guildId, settings.subreddit, channel, post) }
    }

    private fun mirrorPost(guildId: Long, subreddit: String, channel: TextChannel, post: RedditPost) {
        val mirrorId = RedditPostMirror.id(guildId, post.id)
        val existingMirror = redditPostMirrorRepository.findById(mirrorId).orElse(null)
        if (existingMirror != null) {
            redditPostMirrorRepository.save(existingMirror)
            return
        }
        val pendingId = RedditPendingPost.id(guildId, post.id)
        if (redditPendingPostRepository.existsById(pendingId)) {
            return
        }

        redditPendingPostRepository.save(RedditPendingPost(pendingId))
        sendPostMessage(
            channel = channel,
            embed = buildPostEmbed(subreddit, post),
            onSuccess = { message ->
                try {
                    redditPostMirrorRepository.save(
                        RedditPostMirror(
                            id = mirrorId,
                            guildId = guildId,
                            redditPostId = post.id,
                            discordChannelId = channel.idLong,
                            discordMessageId = message.idLong,
                            publishedAt = post.publishedAt,
                            permalink = post.permalink
                        )
                    )
                } finally {
                    redditPendingPostRepository.deleteById(pendingId)
                }
            },
            onFailure = { exception ->
                redditPendingPostRepository.deleteById(pendingId)
                LOG.warn("Failed to mirror Reddit post {} for guild {}", post.id, guildId, exception)
            }
        )
    }

    private fun cleanupRemovedPosts(guildId: Long, posts: List<RedditPost>) {
        val currentPostIds = posts.mapTo(mutableSetOf()) { it.id }
        val oldestFeedPost = posts.minByOrNull { it.publishedAt } ?: return
        redditPostMirrorRepository.findAll()
            .filter { it.guildId == guildId && !it.deleted && it.discordChannelId != null && it.discordMessageId != null }
            .filter { it.redditPostId !in currentPostIds && !it.publishedAt.isBefore(oldestFeedPost.publishedAt) }
            .forEach { mirror -> deleteMirroredMessage(mirror) }
    }

    private fun deleteMirroredMessage(mirror: RedditPostMirror) {
        val channelId = mirror.discordChannelId ?: return
        val messageId = mirror.discordMessageId ?: return
        val channel = jda.getTextChannelById(channelId) ?: return
        channel.deleteMessageById(messageId).queue(
            {
                mirror.deleted = true
                redditPostMirrorRepository.save(mirror)
            },
            { exception ->
                if (isTerminalMessageFailure(exception)) {
                    mirror.deleted = true
                    redditPostMirrorRepository.save(mirror)
                    return@queue
                }
                LOG.warn("Failed to delete mirrored Reddit post message {}", messageId, exception)
            }
        )
    }

    private fun disableMissingChannel(settings: RedditAlertSettings, channelId: Long) {
        settings.channelId = null
        redditAlertSettingsRepository.save(settings)
        LOG.warn("Disabled Reddit alerts for guild {} because channel {} no longer exists", settings.guildId, channelId)
    }

    internal fun sendPostMessage(
        channel: TextChannel,
        embed: MessageEmbed,
        onSuccess: (Message) -> Unit,
        onFailure: (Throwable) -> Unit
    ) {
        channel.sendMessageEmbeds(embed).queue(onSuccess, onFailure)
    }

    internal fun buildPostEmbed(subreddit: String, post: RedditPost): MessageEmbed {
        val embed = EmbedBuilder()
            .setColor(Color(255, 69, 0))
            .setTitle(truncate(post.title, MessageEmbed.TITLE_MAX_LENGTH), post.permalink)
            .setDescription("New post on r/$subreddit")
            .setTimestamp(post.publishedAt)
            .addField("Author", post.author?.let { "u/$it" } ?: "Unknown", true)
            .addField("Reddit", truncate(post.permalink, MessageEmbed.VALUE_MAX_LENGTH), false)

        val thumbnailUrl = post.thumbnailUrl
        if (thumbnailUrl != null) {
            embed.setImage(thumbnailUrl)
        }

        return embed.build()
    }

    internal fun isTerminalMessageFailure(exception: Throwable): Boolean {
        val errorResponseException = exception as? ErrorResponseException ?: return false
        return when (errorResponseException.errorResponse) {
            ErrorResponse.MISSING_PERMISSIONS,
            ErrorResponse.MISSING_ACCESS,
            ErrorResponse.UNKNOWN_CHANNEL,
            ErrorResponse.UNKNOWN_MESSAGE -> true

            else -> false
        }
    }

    private fun truncate(value: String, maxLength: Int): String {
        if (value.length <= maxLength) {
            return value
        }

        return value.take(maxLength - 3) + "..."
    }
}
