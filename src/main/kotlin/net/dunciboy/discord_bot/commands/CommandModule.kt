/*
 * Copyright 2017 Duncan C.
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

package net.dunciboy.discord_bot.commands

import net.dunciboy.discord_bot.sequence.Sequence
import net.dv8tion.jda.core.MessageBuilder
import net.dv8tion.jda.core.entities.ChannelType
import net.dv8tion.jda.core.events.message.MessageReceivedEvent
import net.dv8tion.jda.core.exceptions.PermissionException
import net.dv8tion.jda.core.hooks.ListenerAdapter
import net.dv8tion.jda.core.utils.SimpleLog
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
            throw IllegalArgumentException("aliases needs to contain at least one alias.")
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
            if (event.message.content.length - 1 >= command.length + 2) {
                arguments = messageContent.substring(command.length + 2)
            } else {
                arguments = null
            }
            if (Arrays.stream(aliases).anyMatch { s -> s.equals(command, ignoreCase = true) }) {
                try {
                    commandExec(event, command, arguments)
                    LOG.info("Bot " + event.jda.selfUser.toString() + " on channel " + (if (event.guild != null) event.guild.toString() + " " else "") + event.channel.name + " completed executing " + event.message.content + " command from user " + event.author.toString())
                } catch (pe: PermissionException) {
                    LOG.warn("Bot " + event.jda.selfUser.toString() + " on channel " + (if (event.guild != null) event.guild.toString() + " " else "") + event.channel.name + " failed executing " + event.message.content + " command from user " + event.author.toString())
                    val exceptionMessage = MessageBuilder().append("Cannot complete action due to a permission issue, see the message below for details.").appendCodeBlock(pe.javaClass.simpleName + ": " + pe.message, "text").build()
                    event.channel.sendMessage(exceptionMessage).queue { it.delete().queueAfter(5, TimeUnit.MINUTES) }

                } catch (t: Throwable) {
                    LOG.fatal("Bot " + event.jda.selfUser.toString() + " on channel " + (if (event.guild != null) event.guild.toString() + " " else "") + event.channel.name + " failed executing " + event.message.content + " command from user " + event.author.toString())
                    LOG.log(t)
                    val exceptionMessage = MessageBuilder().append("Cannot complete action due to an error, see the message below for details.").appendCodeBlock(t.javaClass.simpleName + ": " + t.message, "text").build()
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
