package be.duncanc.discordmodbot.roles.persistence

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.test.context.TestConstructor

@DataJpaTest
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class IAmRolesRepositoryTest(
    private val iAmRolesRepository: IAmRolesRepository
) {

    @Test
    fun `category names can be reused across guilds`() {
        iAmRolesRepository.saveAndFlush(
            IAmRolesCategory(guildId = 1L, categoryName = "Games", allowedRoles = 0, roles = mutableSetOf(10L))
        )
        iAmRolesRepository.saveAndFlush(
            IAmRolesCategory(guildId = 2L, categoryName = "Games", allowedRoles = 0, roles = mutableSetOf(20L))
        )

        assertEquals(1, iAmRolesRepository.findByGuildId(1L).count())
        assertEquals(1, iAmRolesRepository.findByGuildId(2L).count())
    }

    @Test
    fun `category names remain unique within a guild`() {
        iAmRolesRepository.saveAndFlush(IAmRolesCategory(guildId = 1L, categoryName = "Games", allowedRoles = 0))

        assertThrows<DataIntegrityViolationException> {
            iAmRolesRepository.saveAndFlush(IAmRolesCategory(guildId = 1L, categoryName = "Games", allowedRoles = 0))
        }
    }
}
