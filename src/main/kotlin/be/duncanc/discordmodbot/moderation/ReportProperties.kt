package be.duncanc.discordmodbot.moderation

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.bind.DefaultValue
import java.time.Duration

@ConfigurationProperties("discord-mod-bot.reporting")
data class ReportProperties(
    @DefaultValue("5m")
    val reportRateLimit: Duration = Duration.ofMinutes(5),
    @DefaultValue("1h")
    val reportedMessageRetention: Duration = Duration.ofHours(1)
)
