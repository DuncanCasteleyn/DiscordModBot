package be.duncanc.discordmodbot.roles

import net.dv8tion.jda.api.events.role.RoleDeleteEvent
import net.dv8tion.jda.api.hooks.ListenerAdapter
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class RoleCategoryCleanupListener(
    private val iAmRolesService: IAmRolesService
) : ListenerAdapter() {
    @Transactional
    override fun onRoleDelete(event: RoleDeleteEvent) {
        iAmRolesService.removeRole(event.guild.idLong, event.role.idLong)
    }
}
