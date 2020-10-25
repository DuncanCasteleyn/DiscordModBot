package be.duncanc.discordmodbot.data.repositories

import be.duncanc.discordmodbot.data.entities.DiscordMessage
import org.springframework.data.repository.CrudRepository

interface DiscordMessageRepository : CrudRepository<DiscordMessage, Long>
