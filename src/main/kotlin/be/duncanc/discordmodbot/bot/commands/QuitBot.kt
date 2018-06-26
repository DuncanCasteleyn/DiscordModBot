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

package be.duncanc.discordmodbot.bot.commands

import net.dv8tion.jda.core.events.message.MessageReceivedEvent
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.config.ConfigurableBeanFactory
import org.springframework.context.ApplicationContext
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.context.annotation.Scope
import org.springframework.stereotype.Component
import java.util.*

/**
 * Quit command for the bot
 */
@Component
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
class QuitBot
@Autowired constructor(
        private val applicationContext: ApplicationContext
) : CommandModule(
        ALIASES,
        null,
        DESCRIPTION,
        true,
        true
) {
    companion object {
        private val ALIASES = arrayOf("Quit", "Shutdown", "Disconnect")
        private const val DESCRIPTION = "Shuts down the bot."
    }

    private val callBeforeBotQuit: MutableList<BeforeBotQuit>

    init {
        callBeforeBotQuit = ArrayList()
    }

    public override fun commandExec(event: MessageReceivedEvent, command: String, arguments: String?) {
        if (event.author.idLong == 159419654148718593L) {
            callBeforeBotQuit.forEach { beforeBotQuit ->
                try {
                    beforeBotQuit.onAboutToQuit()
                } catch (e: Exception) {
                    CommandModule.LOG.error("Failed executing a task before quit due to an exception", e)
                }
            }
            event.channel.sendMessage(event.author.asMention + " **Shutting down... :wave: **").queue()
            /*try {
                TimeUnit.SECONDS.sleep(5);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }*/
            event.jda.shutdown()
            (applicationContext as ConfigurableApplicationContext).close()
        } else {
            event.channel.sendMessage(event.author.asMention + " you don't have permission to shutdown the bot!").queue()
        }

    }

    fun addCallBeforeQuit(beforeBotQuit: BeforeBotQuit) {
        callBeforeBotQuit.add(beforeBotQuit)
    }

    /**
     * This interface can be used to perform actions before quiting
     */
    interface BeforeBotQuit {
        /**
         * Actions to perform before the quit command is finished with executing.
         */
        fun onAboutToQuit()
    }
}
