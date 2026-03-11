package be.duncanc.discordmodbot.membergate.persistence


import org.springframework.data.jpa.repository.JpaRepository

interface WelcomeMessageRepository : JpaRepository<WelcomeMessage, WelcomeMessage.WelcomeMessageId> {
    fun findByGuildId(guildId: Long): Collection<WelcomeMessage>

    fun removeAllByGuildId(guildId: Long)
}
