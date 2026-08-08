package be.duncanc.discordmodbot.logging

import jakarta.validation.constraints.Positive
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.validation.annotation.Validated

@ConfigurationProperties("discord-mod-bot.attachment-cache")
@Validated
class AttachmentCacheProperties(
    @field:Positive
    val channelId: Long = throw IllegalStateException("attachment cache channel ID not configured")
)
