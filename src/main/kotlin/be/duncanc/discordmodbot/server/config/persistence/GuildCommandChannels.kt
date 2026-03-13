package be.duncanc.discordmodbot.server.config.persistence

import jakarta.persistence.*

@Entity
@Table(name = "guild_command_channels")
data class GuildCommandChannels(
    @Id
    @Column(updatable = false)
    val guildId: Long,
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "command_channels_list")
    val whitelistedChannels: MutableSet<Long> = HashSet()
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as GuildCommandChannels

        return guildId == other.guildId
    }

    override fun hashCode(): Int {
        return guildId.hashCode()
    }
}
