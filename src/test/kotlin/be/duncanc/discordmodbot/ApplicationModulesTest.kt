package be.duncanc.discordmodbot

import org.junit.jupiter.api.Test
import org.springframework.modulith.core.ApplicationModules
import kotlin.test.assertEquals

class ApplicationModulesTest {

    @Test
    fun verifiesApplicationModules() {
        val modules = ApplicationModules.of(Application::class.java).verify()

        assertEquals(
            setOf(
                "bootstrap",
                "discord",
                "logging",
                "member.gate",
                "moderation",
                "reporting",
                "roles",
                "server.config",
                "utility",
                "voting",
            ),
            modules.stream().map { it.identifier.toString() }.toList().toSet(),
        )
        assertEquals(
            setOf("bootstrap", "discord"),
            modules.sharedModules.map { it.identifier.toString() }.toSet(),
        )
    }
}
