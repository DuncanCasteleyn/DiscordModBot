package be.duncanc.discordmodbot.bot.config

import be.duncanc.discordmodbot.bot.commands.Command
import be.duncanc.discordmodbot.data.configs.properties.DiscordModBotConfig
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.JDABuilder
import net.dv8tion.jda.api.hooks.ListenerAdapter
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
            GatewayIntent.GUILD_EMOJIS_AND_STICKERS,
            GatewayIntent.GUILD_MEMBERS,
            GatewayIntent.GUILD_MESSAGES,
            GatewayIntent.MESSAGE_CONTENT,
            GatewayIntent.GUILD_MESSAGE_REACTIONS,
            GatewayIntent.GUILD_PRESENCES
        )
    }

    @Bean(destroyMethod = "shutdown")
    fun jda(
        listenerAdapters: Array<ListenerAdapter>,
        commands: Array<Command>,
        discordModBotConfig: DiscordModBotConfig
    ): JDA {
        val jda = JDABuilder.create(discordModBotConfig.botToken, INTENTS)
            .setBulkDeleteSplittingEnabled(false)
            .disableCache(CacheFlag.VOICE_STATE)
            .addEventListeners(*listenerAdapters)
            .setEnableShutdownHook(false)
            .build()

        val updateCommands = jda.updateCommands()
        commands.forEach {
            updateCommands.addCommands(it.getCommandsData())
        }
        updateCommands.queue()

        return jda
    }
}
