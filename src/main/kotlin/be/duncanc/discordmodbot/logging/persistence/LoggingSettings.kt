package be.duncanc.discordmodbot.logging.persistence

import jakarta.persistence.*
import jakarta.validation.constraints.NotNull
import org.hibernate.Hibernate

@Entity
@Table(name = "logging_settings")
data class LoggingSettings
    (
    @Id
    @Column(updatable = false)
    val guildId: Long,
    @Column(nullable = false)
    @field:NotNull
    var modLogChannel: Long? = null,
    var userLogChannel: Long? = null,
    var logMessageUpdate: Boolean = true,
    var logMessageDelete: Boolean = true,
    var logMemberJoin: Boolean = true,
    var logMemberLeave: Boolean = true,
    var logMemberBan: Boolean = true,
    var logMemberRemoveBan: Boolean = true,
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "logging_ignored_channels")
    val ignoredChannels: MutableSet<Long> = HashSet()
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || Hibernate.getClass(this) != Hibernate.getClass(other)) return false
        other as LoggingSettings

        return guildId == other.guildId
    }

    override fun hashCode(): Int = javaClass.hashCode()

    @Override
    override fun toString(): String {
        return this::class.simpleName + "(guildId = $guildId )"
    }

}
