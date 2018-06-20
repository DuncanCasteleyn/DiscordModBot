package be.duncanc.discordmodbot.data.entities

import javax.persistence.CollectionTable
import javax.persistence.ElementCollection
import javax.persistence.Entity
import javax.persistence.Id

@Entity
data class GuildSettings @JvmOverloads constructor(
        @Id
        val guildId: Long,
        var logMessageDelete: Boolean = true,
        var logMessageUpdate: Boolean = true,
        var logMemberRemove: Boolean = true,
        var logMemberBan: Boolean = true,
        var logMemberAdd: Boolean = true,
        var logMemberRemoveBan: Boolean = true,
        @ElementCollection
        @CollectionTable(name = "ignoredLogChannels")
        val ignoredChannels: Set<Long> = HashSet<Long>()) {

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as GuildSettings

        return guildId == other.guildId
    }

    override fun hashCode(): Int {
        return guildId.hashCode()
    }
}