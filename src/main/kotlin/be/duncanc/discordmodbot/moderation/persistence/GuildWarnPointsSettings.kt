package be.duncanc.discordmodbot.moderation.persistence

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Positive

@Entity
@Table(name = "guild_warn_point_settings")
data class GuildWarnPointsSettings(
    @Id
    val guildId: Long,
    @Positive
    @Column(nullable = false)
    var maxPointsPerReason: Int = 1,
    @Positive
    @Column(nullable = false)
    var announcePointsSummaryLimit: Int = 3,
    @NotNull
    @Column(nullable = false)
    var announceChannelId: Long,
    var overrideWarnCommand: Boolean = false
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as GuildWarnPointsSettings

        return guildId == other.guildId
    }

    override fun hashCode(): Int {
        return guildId.hashCode()
    }
}
