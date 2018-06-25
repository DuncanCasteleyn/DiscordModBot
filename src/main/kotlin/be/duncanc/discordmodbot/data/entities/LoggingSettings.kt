package be.duncanc.discordmodbot.data.entities

import javax.persistence.*

@Entity
@Table(name = "logging_settings")
data class LoggingSettings @JvmOverloads constructor(
        @Id
        val guildId: Long,
        var logMessageDelete: Boolean = true,
        var logMessageUpdate: Boolean = true,
        var logMemberRemove: Boolean = true,
        var logMemberBan: Boolean = true,
        var logMemberAdd: Boolean = true,
        var logMemberRemoveBan: Boolean = true,
        @ElementCollection
        @CollectionTable(name = "logging_ignored_channels")
        val ignoredChannels: Set<Long> = HashSet<Long>()
) {

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as LoggingSettings

        return guildId == other.guildId
    }

    override fun hashCode(): Int {
        return guildId.hashCode()
    }
}