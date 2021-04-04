package be.duncanc.discordmodbot.data.repositories.jpa

import be.duncanc.discordmodbot.data.entities.WelcomeMessage
import be.duncanc.discordmodbot.data.entities.WelcomeMessage.WelcomeMessageId
import org.springframework.data.jpa.repository.JpaRepository

interface WelcomeMessageRepository : JpaRepository<WelcomeMessage, WelcomeMessageId> {
    fun findByGuildId(guildId: Long): Collection<WelcomeMessage>

    fun removeAllByGuildId(guildId: Long)
}
