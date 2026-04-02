package be.duncanc.discordmodbot.roles

import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.Role
import net.dv8tion.jda.api.events.role.RoleDeleteEvent
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@ExtendWith(MockitoExtension::class)
class RoleCategoryCleanupListenerTest {
    @Mock
    private lateinit var iAmRolesService: IAmRolesService

    @Mock
    private lateinit var event: RoleDeleteEvent

    @Mock
    private lateinit var guild: Guild

    @Mock
    private lateinit var role: Role

    @Test
    fun `role delete removes the role from stored categories`() {
        val listener = RoleCategoryCleanupListener(iAmRolesService)
        whenever(event.guild).thenReturn(guild)
        whenever(event.role).thenReturn(role)
        whenever(guild.idLong).thenReturn(1L)
        whenever(role.idLong).thenReturn(10L)

        listener.onRoleDelete(event)

        verify(iAmRolesService).removeRole(1L, 10L)
    }
}
