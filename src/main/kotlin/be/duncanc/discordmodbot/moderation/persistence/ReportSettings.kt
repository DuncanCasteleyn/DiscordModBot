package be.duncanc.discordmodbot.moderation.persistence

import jakarta.persistence.CollectionTable
import jakarta.persistence.Column
import jakarta.persistence.ElementCollection
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.Table
import org.hibernate.Hibernate

@Entity
@Table(name = "report_settings")
data class ReportSettings(
    @Id
    @Column(name = "guild_id", updatable = false)
    val guildId: Long,

    @Column(name = "urgent_role_id")
    var urgentRoleId: Long? = null,

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
        name = "report_settings_blocked_users",
        joinColumns = [JoinColumn(name = "guild_id")]
    )
    @Column(name = "user_id")
    val blockedUserIds: MutableSet<Long> = HashSet()
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || Hibernate.getClass(this) != Hibernate.getClass(other)) return false
        other as ReportSettings

        return guildId == other.guildId
    }

    override fun hashCode(): Int = Hibernate.getClass(this).hashCode()

    override fun toString(): String = "ReportSettings(guildId=$guildId, urgentRoleId=$urgentRoleId)"
}
