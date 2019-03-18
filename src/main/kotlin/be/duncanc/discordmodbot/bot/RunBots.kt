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

import be.duncanc.discordmodbot.bot.utils.ExecutorServiceEventManager
import be.duncanc.discordmodbot.data.configs.properties.DiscordModBotConfig
import net.dv8tion.jda.core.AccountType
import net.dv8tion.jda.core.JDABuilder
import net.dv8tion.jda.core.hooks.ListenerAdapter
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.CommandLineRunner
import org.springframework.context.ApplicationContext
import org.springframework.stereotype.Component


@Component
class RunBots
@Autowired constructor(
    private val applicationContext: ApplicationContext,
    private val discordModBotConfig: DiscordModBotConfig
) : CommandLineRunner {
    companion object {
        const val BOT_THREAD_POOL_SIZE = 3
        internal val LOG = LoggerFactory.getLogger(RunBots::class.java)
    }

    override fun run(vararg args: String?) {
        try {
            discordModBotConfig.botTokens.forEach {
                JDABuilder(AccountType.BOT)
                    .setCorePoolSize(BOT_THREAD_POOL_SIZE)
                    .setEventManager(ExecutorServiceEventManager(it.substring(30)))
                    .setToken(it)
                    .setBulkDeleteSplittingEnabled(false)
                    .addEventListener(*applicationContext.getBeansOfType(ListenerAdapter::class.java).values.toTypedArray())
                    .build()
            }
        } catch (e: Exception) {
            LOG.error("Exception while booting the bots", e)
        }
    }
}
