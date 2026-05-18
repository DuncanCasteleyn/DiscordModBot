package be.duncanc.discordmodbot.narou.novel.api.persistence

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table

@Entity
@Table(name = "narou_novel_alert_settings")
data class NarouNovelAlertSettings(
    @Id
    @Column(updatable = false)
    val guildId: Long,
    @Column(nullable = true)
    var channelId: Long? = null,
    @Column(nullable = true)
    var pingRoleId: Long? = null,
    @Column(nullable = false)
    var lengthThreshold: Long = DEFAULT_LENGTH_THRESHOLD,
    @Column(nullable = true)
    var lastAlertedLength: Long? = null,
    @Column(nullable = true)
    var lastAlertedGeneralAllNo: Int? = null
) {
    companion object {
        const val DEFAULT_LENGTH_THRESHOLD = 1000L
    }
}
