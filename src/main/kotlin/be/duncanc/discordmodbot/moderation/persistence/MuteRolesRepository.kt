package be.duncanc.discordmodbot.moderation.persistence

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional

@Repository
interface MuteRolesRepository : JpaRepository<MuteRole, Long> {
    @Transactional
    fun deleteByRoleIdAndGuildId(roleId: Long, guildId: Long)
}
