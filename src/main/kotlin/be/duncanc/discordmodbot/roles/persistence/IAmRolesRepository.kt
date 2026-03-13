package be.duncanc.discordmodbot.roles.persistence

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional

@Repository
interface IAmRolesRepository : JpaRepository<IAmRolesCategory, IAmRolesCategory.IAmRoleId> {
    @Transactional(readOnly = true)
    fun findByGuildId(guildId: Long): Iterable<IAmRolesCategory>

    @Transactional(readOnly = true)
    fun findByRolesContainsAndGuildId(roles: MutableSet<Long>, guildId: Long): Iterable<IAmRolesCategory>
}
