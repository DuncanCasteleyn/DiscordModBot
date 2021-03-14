package be.duncanc.discordmodbot.data.repositories.jpa

import be.duncanc.discordmodbot.data.entities.WelcomeMessage
import org.springframework.data.jpa.repository.JpaRepository

interface WelcomeMessageRepository : JpaRepository<WelcomeMessage, WelcomeMessage.WelcomeMessageId>
