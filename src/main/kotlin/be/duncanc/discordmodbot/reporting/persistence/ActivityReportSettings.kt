package be.duncanc.discordmodbot.reporting.persistence

import jakarta.persistence.*

@Entity
@Table(name = "activity_report_settings")
data class ActivityReportSettings(
    @Id
    val guildId: Long,
    var reportChannel: Long? = null,
    @ElementCollection(fetch = FetchType.EAGER)
    @Column(name = "tracked")
    val trackedRoleOrMember: MutableSet<Long> = HashSet()
)
