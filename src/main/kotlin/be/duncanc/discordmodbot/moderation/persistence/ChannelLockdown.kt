package be.duncanc.discordmodbot.moderation.persistence

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.IdClass
import jakarta.persistence.Table
import org.hibernate.Hibernate
import java.io.Serializable
import java.util.Objects

@Entity
@IdClass(ChannelLockdown.ChannelLockdownId::class)
@Table(name = "channel_lockdowns")
class ChannelLockdown(
    @Id
    var guildId: Long,
    @Id
    var channelId: Long,
    @Id
    @Enumerated(EnumType.STRING)
    @Column(length = 10)
    var targetType: LockdownTargetType,
    @Id
    var targetId: Long,
    @Column(nullable = false)
    var previousAllow: Long,
    @Column(nullable = false)
    var previousDeny: Long,
    @Column(nullable = false)
    var existed: Boolean
) {
    data class ChannelLockdownId(
        val guildId: Long = 0,
        val channelId: Long = 0,
        val targetType: LockdownTargetType = LockdownTargetType.ROLE,
        val targetId: Long = 0
    ) : Serializable

    final override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || Hibernate.getClass(this) != Hibernate.getClass(other)) return false

        other as ChannelLockdown

        return guildId == other.guildId && channelId == other.channelId &&
                targetType == other.targetType && targetId == other.targetId
    }

    final override fun hashCode(): Int = Objects.hash(guildId, channelId, targetType, targetId)

    override fun toString(): String {
        return "ChannelLockdown(guildId=$guildId, channelId=$channelId, targetType=$targetType, targetId=$targetId)"
    }
}
