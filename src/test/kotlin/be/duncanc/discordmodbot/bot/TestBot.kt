/*
 * Copyright 2018.  Duncan Casteleyn
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

import be.duncanc.discordmodbot.bot.commands.CommandModule
import net.dv8tion.jda.core.AccountType
import net.dv8tion.jda.core.JDA
import net.dv8tion.jda.core.JDABuilder
import net.dv8tion.jda.core.hooks.ListenerAdapter
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.CommandLineRunner
import org.springframework.context.ApplicationContext
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component

/**
 * This main class starts the bot
 */

@Suppress("unused")
@Profile("testing")
@Component
class TestBot @Autowired constructor(
        private val applicationContext: ApplicationContext
) : CommandLineRunner {

    private lateinit var devJDA: JDA

    companion object {
        private val log = LoggerFactory.getLogger(TestBot::class.java)
    }


    override fun run(vararg args: String?) {
        try {
            val configObject = RunBots.loadConfig()

            val devJDABuilder = JDABuilder(AccountType.BOT)
                    .setBulkDeleteSplittingEnabled(false)
                    .setCorePoolSize(RunBots.BOT_THREAD_POOL_SIZE)
                    .setToken(configObject.getString("Dev"))
                    .addEventListener(*applicationContext.getBeansOfType(ListenerAdapter::class.java).values.toTypedArray())

            devJDA = devJDABuilder.buildAsync()
        } catch (e: Exception) {
            log.error("An error occurred while starting the test bot", e)
        }
    }
}