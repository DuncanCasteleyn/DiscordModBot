/*
 * Copyright 2017 Duncan C.
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
