package be.duncanc.discordmodbot.data.repositories

import be.duncanc.discordmodbot.data.entities.LoggingSettings
import org.springframework.data.repository.CrudRepository

interface LoggingSettingsRepository : CrudRepository<LoggingSettings, Long>