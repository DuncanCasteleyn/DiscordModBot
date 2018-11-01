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

import be.duncanc.discordmodbot.data.configs.properties.DiscordModBotConfigurationProperties
import kotlinx.coroutines.*
import net.dv8tion.jda.core.MessageBuilder
import net.dv8tion.jda.core.events.message.MessageReceivedEvent
import org.jetbrains.kotlin.cli.common.environment.setIdeaIoUseFallback
import org.springframework.beans.factory.config.ConfigurableBeanFactory
import org.springframework.context.annotation.Scope
import org.springframework.stereotype.Component
import java.util.concurrent.TimeUnit
import javax.script.ScriptEngineManager
import javax.script.ScriptException

/**
 * This class provides the ability to evaluate code while running.
 *
 * @since 1.1.0
 */
@Component
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
class Eval(
        val discordModBotConfigurationProperties: DiscordModBotConfigurationProperties
) : CommandModule(
        ALIASES,
        DESCRIPTION,
        ARGUMENTATION,
        false,
        true
) {
    companion object {
        private val ALIASES = arrayOf("Eval")
        private const val DESCRIPTION = "Allows you to evaluate code using the JDA library"
        private const val ARGUMENTATION = "<Javascript or Java code>\n" +
                "    Example: `!eval return \"5 + 5 is: \" + (5 + 5);\n" +
                "    This will print: 5 + 5 is: 10"
    }

    init {
        setIdeaIoUseFallback()
    }

    override fun commandExec(event: MessageReceivedEvent, command: String, arguments: String?) {
        if (event.author.idLong != discordModBotConfigurationProperties.ownerId) {
            event.channel.sendMessage("Sorry, this command is for the bot owner only!").queue { it.delete().queueAfter(1, TimeUnit.MINUTES) }
            return
        }

        val messageBuilder = MessageBuilder()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val engine = ScriptEngineManager().getEngineByExtension("kts")!!
                engine.put("event", event)
                engine.eval("import net.dv8tion.jda.core.utils.*\n" +
                        "import net.dv8tion.jda.core.events.message.MessageReceivedEvent\n" +
                        "val event = bindings[\"event\"] as MessageReceivedEvent")
                val future = async {
                    engine.eval(event.message.contentRaw.substring(command.length + 2))
                }

                val out: Any? = try {
                    withTimeout(TimeUnit.SECONDS.toMillis(10)) {
                        future.await()
                    }
                } catch (tce: TimeoutCancellationException) {
                    coroutineContext.cancelChildren()
                    throw tce
                }
                messageBuilder.appendCodeBlock(out?.toString() ?: "Executed without error.", "text")
            } catch (scriptException: ScriptException) {
                messageBuilder.appendCodeBlock("${scriptException.javaClass.simpleName
                        ?: scriptException.javaClass.simpleName}: ${scriptException.message
                        ?: scriptException.message}", "text")
            } catch (throwable: Throwable) {
                messageBuilder.appendCodeBlock("${throwable.javaClass.simpleName}: ${throwable.message}", "text")
                event.channel.sendMessage(messageBuilder.build()).queue()
                throw throwable
            }
            event.channel.sendMessage(messageBuilder.build()).queue()
        }
    }
}