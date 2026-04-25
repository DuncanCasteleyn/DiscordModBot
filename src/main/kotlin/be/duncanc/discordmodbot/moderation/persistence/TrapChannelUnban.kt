package be.duncanc.discordmodbot.moderation.persistence

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.IdClass
import jakarta.persistence.Table
import java.io.Serializable
import java.time.OffsetDateTime

@Entity
@IdClass(TrapChannelUnban.TrapChannelUnbanId::class)
@Table(name = "trap_channel_unbans")
data class TrapChannelUnban(
    @Id
    val guildId: Long,
    @Id
    val userId: Long,
    @Column(nullable = false)
    val unbanAt: OffsetDateTime
) {
    data class TrapChannelUnbanId(
        val guildId: Long = 0,
        val userId: Long = 0
    ) : Serializable
}
