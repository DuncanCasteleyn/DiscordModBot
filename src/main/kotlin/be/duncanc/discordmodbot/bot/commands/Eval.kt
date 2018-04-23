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

import net.dv8tion.jda.core.MessageBuilder
import net.dv8tion.jda.core.entities.ChannelType
import net.dv8tion.jda.core.events.message.MessageReceivedEvent
import java.util.concurrent.*
import javax.script.ScriptEngine
import javax.script.ScriptEngineManager

/**
 * This class provides the ability to evaluate code while running.
 *
 * @since 1.1.0
 */
class Eval : CommandModule(ALIASES, DESCRIPTION, ARGUMENTATION, false) {
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