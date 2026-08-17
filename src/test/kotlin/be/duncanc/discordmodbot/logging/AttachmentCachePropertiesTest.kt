package be.duncanc.discordmodbot.logging

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.context.annotation.Configuration

class AttachmentCachePropertiesTest {

    private val contextRunner = ApplicationContextRunner()
        .withUserConfiguration(AttachmentCachePropertiesConfiguration::class.java)

    @Test
    fun `custom channel ID is bound from properties`() {
        contextRunner
            .withPropertyValues("discord-mod-bot.attachment-cache.channel-id=123456789012345678")
            .run { context ->
                val properties = context.getBean(AttachmentCacheProperties::class.java)
                assertEquals(123456789012345678L, properties.channelId)
            }
    }

    @Configuration
    @EnableConfigurationProperties(AttachmentCacheProperties::class)
    class AttachmentCachePropertiesConfiguration
}
