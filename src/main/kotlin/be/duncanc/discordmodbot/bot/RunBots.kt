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

import be.duncanc.discordmodbot.bot.services.MemberGate
import be.duncanc.discordmodbot.bot.utils.ExecutorServiceEventManager
import net.dv8tion.jda.core.AccountType
import net.dv8tion.jda.core.JDA
import net.dv8tion.jda.core.JDABuilder
import net.dv8tion.jda.core.hooks.ListenerAdapter
import org.json.JSONObject
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.CommandLineRunner
import org.springframework.context.ApplicationContext
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import java.nio.file.Files
import java.nio.file.Paths


@Profile("production")
@Component
class RunBots
@Autowired constructor(
        private val applicationContext: ApplicationContext
) : CommandLineRunner {
    companion object {
        private val configFile = Paths.get("Config.json")
        const val BOT_THREAD_POOL_SIZE = 3
        internal val LOG = LoggerFactory.getLogger(RunBots::class.java)

        fun loadConfig(): JSONObject {
            val configFileContent = StringBuilder()
            Files.readAllLines(configFile).map { configFileContent.append(it) }
            return JSONObject(configFileContent.toString())
        }
    }

    private lateinit var reZeroBot: JDA
    private lateinit var fairyTailBot: JDA


    override fun run(vararg args: String?) {
        try {
            val configObject = loadConfig()

            //Fairy tail bot

            val fairyTailJDABuilder = JDABuilder(AccountType.BOT)
                    .setCorePoolSize(BOT_THREAD_POOL_SIZE)
                    .setEventManager(ExecutorServiceEventManager("Fairy tail"))
                    .setToken(configObject.getString("FairyTail"))
                    .setBulkDeleteSplittingEnabled(false)
                    .addEventListener(*applicationContext.getBeansOfType(ListenerAdapter::class.java).values.toTypedArray())


            //Re:Zero bot

            val reZeroJDABuilder = JDABuilder(AccountType.BOT)
                    .setCorePoolSize(BOT_THREAD_POOL_SIZE)
                    .setToken(configObject.getString("ReZero"))
                    .setEventManager(ExecutorServiceEventManager("Re:Zero"))
                    .setBulkDeleteSplittingEnabled(false)
                    .addEventListener(*applicationContext.getBeansOfType(ListenerAdapter::class.java).values.toTypedArray())

            //TEMP EVENT BOT STARTS HERE
            /*val qAndA = QAndA(
                    arrayOf(arrayOf("Half-elf", "Halfelf", "Half elf"), arrayOf("D43th", "Death"), arrayOf("11/25/17/15", "11 25 17 15"), arrayOf("#9607", "9607"), arrayOf("EMT"), arrayOf("Satella"), arrayOf("Knock knock", "Knock, knock"), arrayOf("Main character"), arrayOf("Unseen hand"), arrayOf("/r/Re_Zero"), arrayOf("Leave Subaru alone"), arrayOf("White whale"), arrayOf("Taida"), arrayOf("Season 2")),
                    longArrayOf(304574219591614464L, 304574251602673664L, 304574277871730690L, 304574317251788802L, 304574347639521290L, 304574375812792340L, 304574398793383936L, 304574423900618752L, 304574446004469760L, 304574470100746240L, 304574505647603712L, 304575734335406080L, 304575767692443648L, 304575845211832322L),
                    304575411084328960L)

            val devVersionEventReZero = JDABuilder(AccountType.BOT)
                    .setToken(configObject.getString("Event"))
                    .addEventListener(qAndA, QuitBot())
                    .buildAsync()*/
            //TEMP EVENT BOT ENDS HERE

            reZeroBot = reZeroJDABuilder.buildAsync()
            fairyTailBot = fairyTailJDABuilder.buildAsync()
        } catch (e: Exception) {
            LOG.error("Exception while booting the bots", e)
        }
    }
}
