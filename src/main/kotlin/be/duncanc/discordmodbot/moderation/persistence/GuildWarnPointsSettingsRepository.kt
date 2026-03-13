package be.duncanc.discordmodbot.moderation.persistence

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface GuildWarnPointsSettingsRepository : JpaRepository<GuildWarnPointsSettings, Long>
