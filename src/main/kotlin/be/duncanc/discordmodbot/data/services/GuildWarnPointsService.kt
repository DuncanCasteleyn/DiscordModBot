package be.duncanc.discordmodbot.data.services

import be.duncanc.discordmodbot.data.entities.GuildWarnPoint
import be.duncanc.discordmodbot.data.repositories.jpa.GuildWarnPointsRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.*

@Service
class GuildWarnPointsService(private val guildWarnPointsRepository: GuildWarnPointsRepository) {

    @Transactional(readOnly = true)
    fun userHasGuildWarnings(guildId: Long, userId: Long): Boolean {
        return guildWarnPointsRepository.existsByGuildIdAndUserId(guildId, userId)
    }

    @Transactional(readOnly = true)
    fun getGuildWarningsFromUser(guildId: Long, userId: Long): Collection<GuildWarnPoint> {
        return guildWarnPointsRepository.findAllByGuildIdAndUserId(guildId, userId)
    }

    @Transactional
    fun revokePoint(id: UUID) {
        guildWarnPointsRepository.deleteAllById(id)
    }
}