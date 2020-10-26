package be.duncanc.discordmodbot.data.repositories

import be.duncanc.discordmodbot.data.entities.DiscordMessage
import org.springframework.data.keyvalue.repository.KeyValueRepository

interface DiscordMessageRepository : KeyValueRepository<DiscordMessage, Long>
