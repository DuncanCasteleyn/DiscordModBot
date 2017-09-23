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

package be.duncanc.discordmodbot.commands

import be.duncanc.discordmodbot.sequence.Sequence
import net.dv8tion.jda.core.MessageBuilder
import net.dv8tion.jda.core.entities.ChannelType
import net.dv8tion.jda.core.events.message.MessageReceivedEvent
import net.dv8tion.jda.core.exceptions.PermissionException
import net.dv8tion.jda.core.hooks.ListenerAdapter
import net.dv8tion.jda.core.utils.SimpleLog
import org.slf4j.event.Level
import java.util.*
import java.util.concurrent.TimeUnit

/**
 * an abstract class that can be used to listen for commands.
 *
 * @property aliases             the description for this command
 * @property argumentationSyntax the syntax for the argumentation of the command, put null if none needed.
 * @property description         The description of the command
 * @property cleanCommandMessage Allows you to enable or disable cleaning up commands.
 * @since 1.0.0
 */
abstract class CommandModule @JvmOverloads protected constructor(internal val aliases: Array<String>, internal val argumentationSyntax: String?, internal val description: String?, private val cleanCommandMessage: Boolean = true) : ListenerAdapter(), ICommandModule {
    companion object {
        const val COMMAND_SIGN: Char = '!'
        protected val LOG: SimpleLog = SimpleLog.getLog(CommandModule::class.java.simpleName)
    }

    init {
        if (aliases.isEmpty()) {
            throw IllegalArgumentException("aliases must contain at least one alias.")
        }
    }

    /**
     * When a message is received it will decide if the message is command that should be handled by the command executor.

     * @param event A message event.
     */
    override fun onMessageReceived(event: MessageReceivedEvent) {
        val messageContent = event.message.rawContent.trim().replace("\\s+".toRegex(), " ")

        if (event.author.isBot || messageContent == "" || event.jda.registeredListeners.stream().anyMatch { o -> o is Sequence && o.user === event.author }) {
            return
        }

        if (messageContent[0] == COMMAND_SIGN) {
            val argumentsArray = messageContent.split(" ")//.dropLastWhile { it.isEmpty() }.toTypedArray()
            val command = argumentsArray[0].substring(1)
            val arguments: String?
            arguments = if (event.message.content.length - 1 >= command.length + 2) {
                messageContent.substring(command.length + 2)
            } else {
                null
            }
            if (Arrays.stream(aliases).anyMatch { s -> s.equals(command, ignoreCase = true) }) {
                try {
                    commandExec(event, command, arguments)
                    LOG.info("Bot " + event.jda.selfUser.toString() + " on channel " + (if (event.guild != null) event.guild.toString() + " " else "") + event.channel.name + " completed executing " + event.message.content + " command from user " + event.author.toString())
                } catch (pe: PermissionException) {
                    LOG.warn("Bot " + event.jda.selfUser.toString() + " on channel " + (if (event.guild != null) event.guild.toString() + " " else "") + event.channel.name + " failed executing " + event.message.content + " command from user " + event.author.toString())
                    val exceptionMessage = MessageBuilder().append("Cannot complete action due to a permission issue; see the message below for details.").appendCodeBlock(pe.javaClass.simpleName + ": " + pe.message, "text").build()
                    event.channel.sendMessage(exceptionMessage).queue { it.delete().queueAfter(5, TimeUnit.MINUTES) }

                } catch (t: Throwable) {
                    LOG.fatal("Bot " + event.jda.selfUser.toString() + " on channel " + (if (event.guild != null) event.guild.toString() + " " else "") + event.channel.name + " failed executing " + event.message.content + " command from user " + event.author.toString())
                    LOG.log(Level.TRACE, t)
                    val exceptionMessage = MessageBuilder().append("Cannot complete action due to an error; see the message below for details.").appendCodeBlock(t.javaClass.simpleName + ": " + t.message, "text").build()
                    event.channel.sendMessage(exceptionMessage).queue { it.delete().queueAfter(5, TimeUnit.MINUTES) }
                }

                try {
                    if (cleanCommandMessage && event.isFromType(ChannelType.TEXT)) {
                        event.message.delete().queue()
                    }
                } catch (e: Exception) {
                    LOG.warn("Bot " + event.jda.selfUser.toString() + " on channel " + (if (event.guild != null) event.guild.toString() + " " else "") + event.channel.name + " failed deleting " + event.message.content + " command from user " + event.author.toString())
                }

            }
        }
    }
}
