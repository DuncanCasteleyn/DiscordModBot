package be.duncanc.discordmodbot.data.repositories

import be.duncanc.discordmodbot.data.entities.BlackListedWord
import be.duncanc.discordmodbot.data.entities.BlackListedWord.BlackListedWordId
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface BlackListedWordRepository : JpaRepository<BlackListedWord, BlackListedWordId> {
    fun findAllByGuildId(guildId: Long): Iterable<BlackListedWord>
}
