package be.duncanc.discordmodbot.discord


import be.duncanc.discordmodbot.bootstrap.DiscordModBotConfig
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.JDABuilder
import net.dv8tion.jda.api.hooks.EventListener
import net.dv8tion.jda.api.requests.GatewayIntent
import net.dv8tion.jda.api.utils.cache.CacheFlag
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class BotConfiguration {
    companion object {
        val INTENTS = listOf(
            GatewayIntent.DIRECT_MESSAGES,
            GatewayIntent.GUILD_MODERATION,
            GatewayIntent.GUILD_EXPRESSIONS,
            GatewayIntent.GUILD_MEMBERS,
            GatewayIntent.GUILD_MESSAGES,
            GatewayIntent.MESSAGE_CONTENT,
            GatewayIntent.GUILD_MESSAGE_REACTIONS,
            GatewayIntent.GUILD_PRESENCES
        )
    }

    @Bean(destroyMethod = "shutdown")
    fun jda(
        eventListeners: Array<EventListener>,
        discordCommands: Array<DiscordCommand>,
        discordModBotConfig: DiscordModBotConfig
    ): JDA {
        val jda = JDABuilder.create(discordModBotConfig.botToken, INTENTS)
            .setBulkDeleteSplittingEnabled(false)
            .disableCache(CacheFlag.VOICE_STATE, CacheFlag.SCHEDULED_EVENTS)
            .addEventListeners(*eventListeners)
            .setEnableShutdownHook(false)
            .build()
            .awaitReady()

        val updateCommands = jda.updateCommands()
        discordCommands.forEach {
            updateCommands.addCommands(it.getCommandsData())
        }
        updateCommands.queue()

        return jda
    }
}
