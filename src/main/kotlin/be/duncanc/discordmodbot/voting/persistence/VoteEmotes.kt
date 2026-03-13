package be.duncanc.discordmodbot.voting.persistence

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table

@Entity
@Table(name = "voting_emotes")
data class VoteEmotes(
    @Id
    val guildId: Long,
    @Column(nullable = false)
    val voteYesEmote: Long,
    @Column(nullable = false)
    val voteNoEmote: Long
) {


    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as VoteEmotes

        if (guildId != other.guildId) return false

        return true
    }

    override fun hashCode(): Int {
        return guildId.hashCode()
    }

    override fun toString(): String {
        return "VoteEmotes(guildId=$guildId, voteYesEmote=$voteYesEmote, voteNoEmote=$voteNoEmote)"
    }
}
