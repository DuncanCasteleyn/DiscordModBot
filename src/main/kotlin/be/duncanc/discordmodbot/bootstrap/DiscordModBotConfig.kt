package be.duncanc.discordmodbot.bootstrap

import jakarta.validation.constraints.NotEmpty
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.validation.annotation.Validated

@ConfigurationProperties("discord-mod-bot")
@Validated
class DiscordModBotConfig(
    val ownerId: Long = throw IllegalStateException("ownerId not configured"),
    @field:NotEmpty
    val botToken: String = throw IllegalStateException("botToken not configured"),
)
