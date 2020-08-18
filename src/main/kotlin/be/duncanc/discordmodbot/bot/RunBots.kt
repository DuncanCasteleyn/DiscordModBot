/*
 * Copyright 2018 Duncan Casteleyn
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package be.duncanc.discordmodbot.bot

import be.duncanc.discordmodbot.data.configs.properties.DiscordModBotConfig
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.JDABuilder
import net.dv8tion.jda.api.hooks.ListenerAdapter
import net.dv8tion.jda.api.requests.GatewayIntent.*
import net.dv8tion.jda.api.utils.cache.CacheFlag
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component


@Component
class RunBots
@Autowired constructor(
        listenerAdapters: Array<ListenerAdapter>,
        discordModBotConfig: DiscordModBotConfig
) {
    companion object {
        val INTENTS = listOf(
                DIRECT_MESSAGES,
                GUILD_BANS,
                GUILD_EMOJIS,
                GUILD_MEMBERS,
                GUILD_MESSAGES,
                GUILD_MESSAGE_REACTIONS,
                GUILD_PRESENCES
        )
    }

    val runningBots: List<JDA> = discordModBotConfig.botTokens.map {
        JDABuilder.create(it, INTENTS)
                .setBulkDeleteSplittingEnabled(false)
                .disableCache(CacheFlag.VOICE_STATE)
                .addEventListeners(*listenerAdapters)
                .build()
    }
}
