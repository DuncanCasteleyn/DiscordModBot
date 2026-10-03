package be.duncanc.discordmodbot.moderation.persistence

import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table

@Entity
@Table(name = "audio_filter_settings")
data class AudioFilterSettings(
    @Id
    val guildId: Long,
    val timeoutMinutes: Long? = null
)
