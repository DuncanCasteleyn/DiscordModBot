package be.duncanc.discordmodbot.logging.persistence

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface LoggingSettingsRepository : JpaRepository<LoggingSettings, Long>
