package be.duncanc.discordmodbot.moderation.persistence

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table

@Entity
@Table(name = "guild_trap_channels")
data class GuildTrapChannel(
    @Id
    val guildId: Long,
    @Column(nullable = false)
    val channelId: Long
)
