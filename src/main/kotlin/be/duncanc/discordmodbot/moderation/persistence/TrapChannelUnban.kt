package be.duncanc.discordmodbot.moderation.persistence

import jakarta.persistence.*
import org.hibernate.Hibernate
import java.io.Serializable
import java.time.OffsetDateTime
import java.util.*

@Entity
@IdClass(TrapChannelUnban.TrapChannelUnbanId::class)
@Table(name = "trap_channel_unbans")
data class TrapChannelUnban(
    @Id
    var guildId: Long,
    @Id
    var userId: Long,
    @Column(nullable = false)
    var unbanAt: OffsetDateTime
) {
    data class TrapChannelUnbanId(
        @Id
        val guildId: Long = 0,
        @Id
        val userId: Long = 0
    ) : Serializable

    final override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || Hibernate.getClass(this) != Hibernate.getClass(other)) return false

        other as TrapChannelUnban

        return guildId != null && guildId == other.guildId && userId != null && userId == other.userId
    }

    final override fun hashCode(): Int = Objects.hash(guildId, userId)

    override fun toString(): String {
        return "TrapChannelUnban(guildId=$guildId, userId=$userId, unbanAt=$unbanAt)"
    }
}
