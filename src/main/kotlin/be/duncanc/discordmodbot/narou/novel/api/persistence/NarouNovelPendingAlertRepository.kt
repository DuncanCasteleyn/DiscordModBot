package be.duncanc.discordmodbot.narou.novel.api.persistence

import org.springframework.data.keyvalue.repository.KeyValueRepository
import org.springframework.stereotype.Repository

@Repository
interface NarouNovelPendingAlertRepository : KeyValueRepository<NarouNovelPendingAlert, Long>
