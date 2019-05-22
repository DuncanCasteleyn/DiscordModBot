package be.duncanc.discordmodbot.data.repositories

import be.duncanc.discordmodbot.data.entities.BlackListedWord
import be.duncanc.discordmodbot.data.entities.BlackListedWord.BlackListedWordId
import org.springframework.data.repository.CrudRepository
import org.springframework.stereotype.Repository

@Repository
interface BlackListedWordRepository : CrudRepository<BlackListedWord, BlackListedWordId> {
    fun findAllByGuildId(guildId: Long): Iterable<BlackListedWord>
}