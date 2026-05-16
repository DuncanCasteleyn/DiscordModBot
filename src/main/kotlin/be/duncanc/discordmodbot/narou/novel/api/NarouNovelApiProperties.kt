package be.duncanc.discordmodbot.narou.novel.api

import jakarta.validation.constraints.NotEmpty
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.bind.DefaultValue
import org.springframework.validation.annotation.Validated
import java.time.Duration

@Validated
@ConfigurationProperties("discord-mod-bot.narou-novel-api")
data class NarouNovelApiProperties(
    @NotEmpty
    @DefaultValue("0/15 * * * * *")
    val pollCron: String,
    @DefaultValue("10s")
    val readTimeout: Duration
)
