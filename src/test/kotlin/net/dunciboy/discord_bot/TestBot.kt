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

package net.dunciboy.discord_bot

import net.dunciboy.discord_bot.commands.Help
import net.dunciboy.discord_bot.commands.QuitBot
import net.dv8tion.jda.core.AccountType
import net.dv8tion.jda.core.JDA
import net.dv8tion.jda.core.JDABuilder
import net.dv8tion.jda.core.utils.SimpleLog

/**
 * This main class starts the bot
 */
class TestBot private constructor(bot: JDA, logToChannel: LogToChannel, logger: GuildLogger) : RunBots(bot, logToChannel, logger) {
    companion object {

        private val LOG = SimpleLog.getLog(TestBot::class.java.simpleName)


        @JvmStatic
        fun main(args: Array<String>) {
            try {
                val configObject = loadConfig()

                val devLogToChannel = LogToChannel()
                val devSettings = Settings()
                val devGuildLogger = GuildLogger(devLogToChannel, devSettings)

                val devJDABuilder = JDABuilder(AccountType.BOT)
                        .setBulkDeleteSplittingEnabled(false)
                        .setCorePoolSize(RunBots.Companion.BOT_THREAD_POOL_SIZE)
                        .setToken(configObject.getString("Dev"))
                        .addEventListener(devGuildLogger, Help(*RunBots.Companion.generalCommands), QuitBot())
                for (generalCommand in RunBots.Companion.generalCommands) {
                    devJDABuilder.addEventListener(generalCommand)
                }

                val devJDA = devJDABuilder.buildAsync()

                RunBots.Companion.bots = arrayOf(TestBot(devJDA, devLogToChannel, devGuildLogger))

                MessageHistory.registerMessageHistory(devJDA)
            } catch (e: Exception) {
                LOG.log(e)
            }
        }
    }
}
