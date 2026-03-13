package be.duncanc.discordmodbot.bootstrap

import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.EnableScheduling

@Configuration
@EnableScheduling
@ConfigurationPropertiesScan("be.duncanc.discordmodbot")
class AppConfig
