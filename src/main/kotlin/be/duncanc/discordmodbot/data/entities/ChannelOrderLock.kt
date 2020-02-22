package be.duncanc.discordmodbot.data.entities

import javax.persistence.Entity
import javax.persistence.Id
import javax.persistence.Table
import javax.persistence.Transient

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
