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
