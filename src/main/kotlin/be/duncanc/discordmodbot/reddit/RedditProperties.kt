package be.duncanc.discordmodbot.reddit

import jakarta.validation.constraints.NotEmpty
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.bind.DefaultValue
import org.springframework.validation.annotation.Validated
import java.time.Duration

@Validated
@ConfigurationProperties("discord-mod-bot.reddit")
data class RedditProperties(
    @NotEmpty
    @DefaultValue("Re_Zero")
    val subreddit: String,
    @NotEmpty
    @DefaultValue("0 */2 * * * *")
    val pollCron: String,
    @DefaultValue("10s")
    val readTimeout: Duration,
    @NotEmpty
    @DefaultValue("DiscordModBot reddit RSS mirror")
    val userAgent: String
)
