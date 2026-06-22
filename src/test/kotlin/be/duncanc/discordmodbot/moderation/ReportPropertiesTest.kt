package be.duncanc.discordmodbot.moderation

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.context.annotation.Configuration
import java.time.Duration

class ReportPropertiesTest {

    private val contextRunner = ApplicationContextRunner()
        .withUserConfiguration(ReportPropertiesConfiguration::class.java)

    @Test
    fun `default values are bound when no properties are set`() {
        contextRunner.run { context ->
            val properties = context.getBean(ReportProperties::class.java)
            assertEquals(Duration.ofMinutes(5), properties.reportRateLimit)
            assertEquals(Duration.ofHours(1), properties.reportedMessageRetention)
        }
    }

    @Test
    fun `custom values are bound from properties`() {
        contextRunner
            .withPropertyValues(
                "discord-mod-bot.reporting.report-rate-limit=10m",
                "discord-mod-bot.reporting.reported-message-retention=2h"
            )
            .run { context ->
                val properties = context.getBean(ReportProperties::class.java)
                assertEquals(Duration.ofMinutes(10), properties.reportRateLimit)
                assertEquals(Duration.ofHours(2), properties.reportedMessageRetention)
            }
    }

    @Configuration
    @EnableConfigurationProperties(ReportProperties::class)
    class ReportPropertiesConfiguration
}
