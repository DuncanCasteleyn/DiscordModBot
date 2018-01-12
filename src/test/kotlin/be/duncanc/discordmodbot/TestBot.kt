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

package be.duncanc.discordmodbot

import be.duncanc.discordmodbot.commands.Help
import net.dv8tion.jda.core.AccountType
import net.dv8tion.jda.core.JDA
import net.dv8tion.jda.core.JDABuilder
import org.slf4j.LoggerFactory

/**
 * This main class starts the bot
 */
class TestBot private constructor(bot: JDA, logToChannel: be.duncanc.discordmodbot.LogToChannel, logger: GuildLogger) : RunBots(bot, logToChannel, logger) {
    companion object {

        private val LOG = LoggerFactory.getLogger(TestBot::class.java)


        @JvmStatic
        fun main(args: Array<String>) {

            try {
                val configObject = loadConfig()

                val devLogToChannel = be.duncanc.discordmodbot.LogToChannel()
                val devGuildLogger = GuildLogger(devLogToChannel)

                val devJDABuilder = JDABuilder(AccountType.BOT)
                        .setBulkDeleteSplittingEnabled(false)
                        .setCorePoolSize(RunBots.Companion.BOT_THREAD_POOL_SIZE)
                        .setToken(configObject.getString("Dev"))
                        .addEventListener(devGuildLogger, Help(), be.duncanc.discordmodbot.commands.QuitBot(), GuildLogger.LogSettings, be.duncanc.discordmodbot.EventsManager(), IAmRoles.INSTANCE)
                for (generalCommand in RunBots.Companion.generalCommands) {
                    devJDABuilder.addEventListener(generalCommand)
                }

                val devJDA = devJDABuilder.buildAsync()

                RunBots.Companion.bots = arrayOf(TestBot(devJDA, devLogToChannel, devGuildLogger))

                be.duncanc.discordmodbot.MessageHistory.registerMessageHistory(devJDA)
            } catch (e: Exception) {
                LOG.error("An error occurred while starting the test bot", e)
            }
        }
    }
}
