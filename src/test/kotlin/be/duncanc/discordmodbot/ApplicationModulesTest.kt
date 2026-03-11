package be.duncanc.discordmodbot

import org.junit.jupiter.api.Test
import org.springframework.modulith.core.ApplicationModules

class ApplicationModulesTest {

    @Test
    fun verifiesApplicationModules() {
        ApplicationModules.of(Application::class.java).verify()
    }
}
