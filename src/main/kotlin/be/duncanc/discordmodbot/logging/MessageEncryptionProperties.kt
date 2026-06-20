package be.duncanc.discordmodbot.logging

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.validation.annotation.Validated

@ConfigurationProperties("discord-mod-bot.message-encryption")
@Validated
class MessageEncryptionProperties(
    @field:NotBlank
    val password: String = throw IllegalStateException("message encryption password not configured"),
    @field:NotBlank
    @field:Pattern(regexp = "([0-9a-fA-F]{2}){16,}", message = "must be an even-length hex value of at least 32 characters")
    val salt: String = throw IllegalStateException("message encryption salt not configured"),
)
