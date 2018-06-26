/*
 * Copyright 2018.  Duncan Casteleyn
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

import net.dv8tion.jda.core.MessageBuilder
import net.dv8tion.jda.core.entities.ChannelType
import net.dv8tion.jda.core.events.message.MessageReceivedEvent
import org.springframework.beans.factory.config.ConfigurableBeanFactory
import org.springframework.context.annotation.Scope
import org.springframework.stereotype.Component
import java.util.concurrent.*
import javax.script.ScriptEngine
import javax.script.ScriptEngineManager

/**
 * This class provides the ability to evaluate code while running.
 *
 * @since 1.1.0
 */
@Component
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
class Eval : CommandModule(
        ALIASES,
        DESCRIPTION,
        ARGUMENTATION,
        false
) {
    companion object {
        private val ALIASES = arrayOf("Eval")
        private const val DESCRIPTION = "Allows you to evaluate code using the JDA library"
        private const val ARGUMENTATION = "<Javascript or Java code>\n" +
                "    Example: `!eval return \"5 + 5 is: \" + (5 + 5);\n" +
                "    This will print: 5 + 5 is: 10"
        private val scriptExecutorService: ExecutorService = Executors.newCachedThreadPool()
    }

    private val engine: ScriptEngine = ScriptEngineManager().getEngineByName("nashorn")!!

    init {
        engine.eval("var imports = new JavaImporter(" +
                "java.io," +
                "java.lang," +
                "java.util," +
                "Packages.net.dv8tion.jda.core," +
                "Packages.net.dv8tion.jda.core.entities," +
                "Packages.net.dv8tion.jda.core.entities.impl," +
                "Packages.net.dv8tion.jda.core.managers," +
                "Packages.net.dv8tion.jda.core.managers.impl," +
                "Packages.net.dv8tion.jda.core.utils," +
                "Packages.be.duncanc.discordmodbot);")

    }

    override fun commandExec(event: MessageReceivedEvent, command: String, arguments: String?) {
        if (event.author.idLong != 159419654148718593L) {
            event.channel.sendMessage("Sorry, this command is for the bot owner only!").queue { it.delete().queueAfter(1, TimeUnit.MINUTES) }
            return
        }

        val messageBuilder = MessageBuilder()

        try {
            engine.put("event", event)
            engine.put("message", event.message)
            engine.put("channel", event.channel)
            engine.put("arguments", arguments)
            engine.put("api", event.jda)
            if (event.isFromType(ChannelType.TEXT)) {
                engine.put("guild", event.guild)
                engine.put("member", event.member)
            }
            val future: Future<*> = scriptExecutorService.submit(Callable {
                engine.eval("(function() {with (imports) {\n" +
                        "$arguments\n" +
                        "}})();")
            })
            val out: Any? = future.get(10, TimeUnit.SECONDS);
            if (!future.isDone) {
                future.cancel(true);
                return
            }
            messageBuilder.appendCodeBlock(out?.toString() ?: "Executed without error.", "text")
        } catch (executionException: ExecutionException) {
            val throwable: Throwable? = executionException.cause
            messageBuilder.appendCodeBlock("${throwable?.javaClass?.simpleName
                    ?: executionException.javaClass.simpleName}: ${throwable?.message
                    ?: executionException.message}", "text")
        } catch (throwable: Throwable) {
            messageBuilder.appendCodeBlock("${throwable.javaClass.simpleName}: ${throwable.message}", "text")
        }
        event.channel.sendMessage(messageBuilder.build()).queue()
    }
}