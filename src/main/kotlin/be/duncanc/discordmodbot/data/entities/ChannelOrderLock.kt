package be.duncanc.discordmodbot.data.entities

import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.Transient

@Entity
@Table(name = "channel_order_locked_guild")
data class ChannelOrderLock(
    @Id
    val guildId: Long,
    val enabled: Boolean = false,
    val unlocked: Boolean = false
) {
    val locked: Boolean
        @Transient
        get() = !unlocked
}
