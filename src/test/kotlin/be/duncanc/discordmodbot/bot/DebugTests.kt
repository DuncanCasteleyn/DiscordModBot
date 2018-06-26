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

import net.dv8tion.jda.core.AccountType
import net.dv8tion.jda.core.JDABuilder
import net.dv8tion.jda.core.events.ReadyEvent
import net.dv8tion.jda.core.exceptions.RateLimitedException
import net.dv8tion.jda.core.hooks.ListenerAdapter
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import javax.security.auth.login.LoginException

/**
 * This class is meant for debugging and logging.
 *
 *
 * Created by Duncan on 1/06/2017.
 */
@SpringBootApplication
class DebugTests {
    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            runApplication<DebugTests>(*args)
            try {
                val configObject = RunBots.loadConfig()

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
                RunBots.LOG.error("Login failed", e)
            } catch (e: RateLimitedException) {
                RunBots.LOG.error("Rate limited", e)
            }
        }
    }
}
