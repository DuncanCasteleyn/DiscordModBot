package be.duncanc.discordmodbot.narou.novel.api.persistence

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface NarouNovelAlertSettingsRepository : JpaRepository<NarouNovelAlertSettings, Long>
