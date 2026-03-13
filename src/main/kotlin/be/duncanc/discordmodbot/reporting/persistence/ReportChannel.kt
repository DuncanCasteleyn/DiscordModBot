package be.duncanc.discordmodbot.reporting.persistence

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.validation.constraints.NotNull

@Entity
@Table(name = "report_channels")
data class ReportChannel
constructor(
    @Id
    @Column(updatable = false)
    val guildId: Long,

    @Column(nullable = false)
    @field:NotNull
    val textChannelId: Long
) {

    override fun equals(other: Any?): Boolean {
        if (this === other) {
            return true
        }
        if (other == null || javaClass != other.javaClass) {
            return false
        }

        val that = other as ReportChannel

        return guildId == that.guildId
    }

    override fun hashCode(): Int = guildId.hashCode()

    override fun toString(): String {
        return "ReportChannel(guildId=$guildId, textChannelId=$textChannelId)"
    }
}
