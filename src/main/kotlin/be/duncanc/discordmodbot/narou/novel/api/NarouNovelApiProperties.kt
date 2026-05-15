package be.duncanc.discordmodbot.narou.novel.api

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

@ConfigurationProperties("discord-mod-bot.narou-novel-api")
class NarouNovelApiProperties(
    val pollCron: String = DEFAULT_POLL_CRON,
    val requestTimeout: Duration = DEFAULT_REQUEST_TIMEOUT
) {
    companion object {
        const val DEFAULT_POLL_CRON = "0/15 * * * * *"
        val DEFAULT_REQUEST_TIMEOUT: Duration = Duration.ofSeconds(5)
    }
}
