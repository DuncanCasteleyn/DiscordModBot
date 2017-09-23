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

import net.dv8tion.jda.core.AccountType
import net.dv8tion.jda.core.JDA
import net.dv8tion.jda.core.JDABuilder
import net.dv8tion.jda.core.events.ReadyEvent
import net.dv8tion.jda.core.exceptions.RateLimitedException
import net.dv8tion.jda.core.hooks.ListenerAdapter
import org.slf4j.event.Level
import javax.security.auth.login.LoginException

/**
 * This class is meant for debugging and logging.
 *
 *
 * Created by Duncan on 1/06/2017.
 */
class DebugTests internal constructor(bot: JDA, logToChannel: be.duncanc.discordmodbot.LogToChannel, logger: GuildLogger) : RunBots(bot, logToChannel, logger) {

    init {
        throw AssertionError()
    }

    companion object {

        @JvmStatic
        fun main(args: Array<String>) {
            try {
                val configObject = loadConfig()

                JDABuilder(AccountType.BOT)
                        .setCorePoolSize(RunBots.BOT_THREAD_POOL_SIZE)
                        .setToken(configObject.getString("ReZero"))
                        .setBulkDeleteSplittingEnabled(false)
                        .addEventListener(object : ListenerAdapter() {
                            override fun onReady(event: ReadyEvent) {
                                //do something
                            }
                        })
                        .buildAsync()
            } catch (e: LoginException) {
                RunBots.LOG.log(Level.ERROR, e)
            } catch (e: RateLimitedException) {
                RunBots.LOG.log(Level.ERROR, e)
            }

        }
    }
}
