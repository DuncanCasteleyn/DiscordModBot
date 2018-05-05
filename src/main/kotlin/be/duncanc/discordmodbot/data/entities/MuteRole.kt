package be.duncanc.discordmodbot.data.entities

import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.Id
import javax.persistence.Table
import javax.validation.constraints.NotNull

@Entity
@Table(name = "mute_roles")
data class MuteRole(
        @Id
        val guildId: Long? = null,

        @NotNull
        @Column(nullable = false)
        val roleId: Long ? = null) {

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as MuteRole

        return guildId == other.guildId
    }

    override fun hashCode(): Int {
        return guildId?.hashCode() ?: 0
    }

    override fun toString(): String {
        return "MuteRole(guildId=$guildId, roleId=$roleId)"
    }
}