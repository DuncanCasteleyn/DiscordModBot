package be.duncanc.discordmodbot.moderation.persistence

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface StickyRoleSnapshotRepository : JpaRepository<StickyRoleSnapshot, StickyRoleSnapshot.StickyRoleSnapshotId> {
    fun findAllByGuildId(guildId: Long): List<StickyRoleSnapshot>

    fun deleteAllByGuildId(guildId: Long)
}
