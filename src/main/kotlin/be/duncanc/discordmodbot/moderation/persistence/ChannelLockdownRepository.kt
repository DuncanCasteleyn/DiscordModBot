package be.duncanc.discordmodbot.moderation.persistence

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional

@Repository
interface ChannelLockdownRepository : JpaRepository<ChannelLockdown, ChannelLockdown.ChannelLockdownId> {
    fun findAllByGuildIdAndChannelId(guildId: Long, channelId: Long): List<ChannelLockdown>

    fun existsByGuildIdAndChannelId(guildId: Long, channelId: Long): Boolean

    @Transactional
    fun deleteAllByGuildIdAndChannelId(guildId: Long, channelId: Long)

    @Transactional
    fun deleteAllByGuildId(guildId: Long)
}
