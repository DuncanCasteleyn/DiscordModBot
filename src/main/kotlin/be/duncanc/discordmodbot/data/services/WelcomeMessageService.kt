package be.duncanc.discordmodbot.data.services

import be.duncanc.discordmodbot.data.entities.WelcomeMessage
import be.duncanc.discordmodbot.data.repositories.jpa.WelcomeMessageRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class WelcomeMessageService(
    val welcomeMessageRepository: WelcomeMessageRepository
) {
    @Transactional(readOnly = true)
    fun getWelcomeMessages(guildId: Long): Collection<WelcomeMessage> {
        return welcomeMessageRepository.findByGuildId(guildId)
    }

    @Transactional
    fun removeWelcomeMessage(welcomeMessage: WelcomeMessage) {
        welcomeMessageRepository.delete(welcomeMessage)
    }

    @Transactional
    fun addWelcomeMessage(welcomeMessage: WelcomeMessage) {
        welcomeMessageRepository.save(welcomeMessage)
    }

    fun removeAllWelcomeMessages(guildId: Long) {
        welcomeMessageRepository.removeAllByGuildId(guildId)
    }
}
