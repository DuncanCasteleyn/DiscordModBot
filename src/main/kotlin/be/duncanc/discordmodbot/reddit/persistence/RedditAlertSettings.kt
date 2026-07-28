package be.duncanc.discordmodbot.reddit.persistence

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table

@Entity
@Table(name = "reddit_alert_settings")
class RedditAlertSettings(
    @Id
    @Column(updatable = false)
    val guildId: Long,
    @Column(nullable = true)
    var channelId: Long? = null,
    @Column(nullable = false, length = 100)
    var subreddit: String
) {
    constructor() : this(0L, null, "")

    override fun equals(other: Any?): Boolean {
        if (this === other) {
            return true
        }
        if (javaClass != other?.javaClass) {
            return false
        }
        other as RedditAlertSettings
        return guildId == other.guildId
    }

    override fun hashCode(): Int = guildId.hashCode()
}
