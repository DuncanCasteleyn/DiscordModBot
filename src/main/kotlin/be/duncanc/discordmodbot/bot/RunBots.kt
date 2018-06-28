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

import be.duncanc.discordmodbot.bot.commands.CommandModule
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

            //todo Make configurable.
            val memberGate = MemberGate(175856762677624832L, 319590906523156481L, 175856762677624832L, 319623491970400268L, 218085411686318080L, arrayOf(MemberGate.WelcomeMessage("https://cafekuyer.files.wordpress.com/2016/04/subaru-ftw.gif", "Welcome to the /r/Re_Zero discord server!"), MemberGate.WelcomeMessage("https://static.tumblr.com/0549f3836351174ea8bba0306ebd2641/cqk1twd/DJcofpaji/tumblr_static_tumblr_static_j2yt5g46evscw4o0scs0co0k_640.gif", "Welcome to the /r/Re_Zero discord server!"), MemberGate.WelcomeMessage("http://pa1.narvii.com/6165/5e28d55b439501172d35f74bc8fe2ac1665af8cf_hq.gif", "Welcome to the /r/Re_Zero discord server I suppose!"), MemberGate.WelcomeMessage("https://i.imgur.com/6HhzYIL.gif", "Welcome to the /r/Re_Zero discord server!"), MemberGate.WelcomeMessage("https://68.media.tumblr.com/e100d53dc43d09f624a9bcb930ad6c8c/tumblr_ofs0sbPbS31uqt6z2o1_500.gif", "Welcome to the /r/Re_Zero discord server!"), MemberGate.WelcomeMessage("https://i.imgbox.com/674W2nGm.gif", "Welcome to the /r/Re_Zero discord server!"), MemberGate.WelcomeMessage("https://i.imgur.com/1zXLZ6E.gif", "Welcome to the /r/Re_Zero discord server!"), MemberGate.WelcomeMessage("https://68.media.tumblr.com/14273c92b69c96d4c71104ed5420b2c8/tumblr_o92199bqfv1qehrvso2_500.gif", "Hiya! Welcome to the /r/Re_Zero discord server nya!")))

            val reZeroJDABuilder = JDABuilder(AccountType.BOT)
                    .setCorePoolSize(BOT_THREAD_POOL_SIZE)
                    .setToken(configObject.getString("ReZero"))
                    .setEventManager(ExecutorServiceEventManager("Re:Zero"))
                    .setBulkDeleteSplittingEnabled(false)
                    .addEventListener(*applicationContext.getBeansOfType(ListenerAdapter::class.java).values.toTypedArray(), memberGate)

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
