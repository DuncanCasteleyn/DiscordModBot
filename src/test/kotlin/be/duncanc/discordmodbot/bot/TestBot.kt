/*
 * MIT License
 *
 * Copyright (c) 2017 Duncan Casteleyn
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 *
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
                    .addEventListener(CommandModule.CommandTextChannelsWhitelist)
                    .addEventListener(*applicationContext.getBeansOfType(ListenerAdapter::class.java).values.toTypedArray())

            devJDA = devJDABuilder.buildAsync()
        } catch (e: Exception) {
            log.error("An error occurred while starting the test bot", e)
        }
    }
}