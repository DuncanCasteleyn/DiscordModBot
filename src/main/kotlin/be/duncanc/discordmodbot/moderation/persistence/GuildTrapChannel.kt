package be.duncanc.discordmodbot.moderation.persistence

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.Hibernate

@Entity
@Table(name = "guild_trap_channels")
class GuildTrapChannel(
    @Id
    var guildId: Long,
    @Column(nullable = false)
    var channelId: Long
) {
    final override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || Hibernate.getClass(this) != Hibernate.getClass(other)) return false

        other as GuildTrapChannel

        return guildId == other.guildId
    }

    final override fun hashCode(): Int = guildId.hashCode()
}
