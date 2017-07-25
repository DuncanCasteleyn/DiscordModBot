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

import net.dv8tion.jda.core.AccountType
import net.dv8tion.jda.core.JDA
import net.dv8tion.jda.core.JDABuilder
import net.dv8tion.jda.core.events.ReadyEvent
import net.dv8tion.jda.core.exceptions.RateLimitedException
import net.dv8tion.jda.core.hooks.ListenerAdapter

import javax.security.auth.login.LoginException

/**
 * This class is meant for debugging and logging.
 *
 *
 * Created by Duncan on 1/06/2017.
 */
class DebugTests internal constructor(bot: JDA, logToChannel: LogToChannel, logger: GuildLogger) : RunBots(bot, logToChannel, logger) {

    init {
        throw AssertionError()
    }

    companion object {

        @JvmStatic fun main(args: Array<String>) {
            try {
                JDABuilder(AccountType.BOT)
                        .setCorePoolSize(RunBots.Companion.BOT_THREAD_POOL_SIZE)
                        .setToken("MjM1NTI5MjMyNDI2NTk4NDAx.C5TDfw.8MhLiD6CQmNbc4SkN1KeyxumOcM")
                        .setBulkDeleteSplittingEnabled(false)
                        .addEventListener(object : ListenerAdapter() {
                            override fun onReady(event: ReadyEvent?) {
                                event!!.jda.getGuildById(160450060436504578L).controller.ban("12345", 1, "For science").queue()
                            }
                        })
                        .buildAsync()
            } catch (e: LoginException) {
                RunBots.Companion.LOG.log(e)
            } catch (e: RateLimitedException) {
                RunBots.Companion.LOG.log(e)
            }

        }
    }
}
