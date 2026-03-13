package be.duncanc.discordmodbot.moderation.persistence

import jakarta.persistence.*
import jakarta.validation.constraints.NotNull

@Entity
@Table(name = "mute_roles")
data class MuteRole
constructor(
    @Id
    @Column(updatable = false)
    val guildId: Long,

    @field:NotNull
    @Column(nullable = false)
    val roleId: Long,

    @ElementCollection
    @CollectionTable(name = "muted_users")
    val mutedUsers: MutableSet<Long> = HashSet()
) {

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as MuteRole

        return guildId == other.guildId
    }

    override fun hashCode(): Int {
        return guildId.hashCode()
    }

    override fun toString(): String {
        return "MuteRole(guildId=$guildId, roleId=$roleId)"
    }
}
