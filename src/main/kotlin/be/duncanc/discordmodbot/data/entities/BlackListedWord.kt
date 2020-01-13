package be.duncanc.discordmodbot.data.entities

import be.duncanc.discordmodbot.data.entities.BlackListedWord.BlackListedWordId
import be.duncanc.discordmodbot.data.entities.BlackListedWord.FilterMethod.EXACT
import java.io.Serializable
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.Id
import javax.persistence.IdClass

@Entity
@IdClass(BlackListedWordId::class)
data class BlackListedWord(
        @Id
        val guildId: Long,
        @Id
        val word: String,
        @Column(nullable = false)
        val filterMethod: FilterMethod = EXACT
) {


    enum class FilterMethod {
        EXACT,
        STARTS_WITH,
        ENDS_WITH,
        CONTAINS
    }

    data class BlackListedWordId(
            @Id
            val guildId: Long? = null,
            @Id
            val word: String? = null
    ) : Serializable

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as BlackListedWord

        if (guildId != other.guildId) return false
        if (word != other.word) return false

        return true
    }

    override fun hashCode(): Int {
        var result = guildId.hashCode()
        result = 31 * result + word.hashCode()
        return result
    }
}
