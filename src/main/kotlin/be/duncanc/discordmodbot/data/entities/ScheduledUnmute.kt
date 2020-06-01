package be.duncanc.discordmodbot.data.entities

import java.io.Serializable
import java.time.OffsetDateTime
import javax.persistence.*

@Entity
@Table(name = "scheduled_unmutes")
@IdClass(ScheduledUnmute.ScheduledUnmuteId::class)
data class ScheduledUnmute(
        @Id
        val guildId: Long,
        @Id
        val userId: Long,
        @Column(nullable = false)
        val unmuteDateTime: OffsetDateTime
) {

    data class ScheduledUnmuteId(
            @Id
            val guildId: Long? = null,
            @Id
            val userId: Long? = null
    ) : Serializable
}
