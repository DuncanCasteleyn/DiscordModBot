package be.duncanc.discordmodbot.reddit.persistence

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table

@Entity
@Table(name = "reddit_alert_settings")
data class RedditAlertSettings(
    @Id
    @Column(updatable = false)
    val guildId: Long,
    @Column(nullable = true)
    var channelId: Long? = null,
    @Column(nullable = false, length = 100)
    var subreddit: String = DEFAULT_SUBREDDIT
) {
    companion object {
        const val DEFAULT_SUBREDDIT = "Re_Zero"
    }
}
