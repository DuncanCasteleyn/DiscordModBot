package be.duncanc.discordmodbot.moderation

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

@ConfigurationProperties("discord-mod-bot.reporting")
data class ReportProperties(
    val reportRateLimit: Duration = Duration.ofMinutes(5)
)
