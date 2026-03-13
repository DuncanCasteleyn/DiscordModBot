package be.duncanc.discordmodbot.reporting.persistence

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface ActivityReportSettingsRepository : JpaRepository<ActivityReportSettings, Long>
