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

import be.duncanc.discordmodbot.bot.sequences.Sequence
import be.duncanc.discordmodbot.data.services.UserBlockService
import net.dv8tion.jda.api.MessageBuilder
import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.entities.ChannelType
import net.dv8tion.jda.api.entities.User
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import net.dv8tion.jda.api.exceptions.PermissionException
import net.dv8tion.jda.api.hooks.ListenerAdapter
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.time.Duration
import java.time.Instant
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.collections.HashMap

/**
 * an abstract class that can be used to listen for commands.
 *
 * @property aliases             the description for this command
 * @property argumentationSyntax the syntax for the argumentation of the command, put null if none needed.
 * @property description         The description of the command
 * @property cleanCommandMessage Allows you to enable or disable cleaning up commands.
 * @since 1.0.0
 */
abstract class CommandModule
@JvmOverloads protected constructor(
    internal open val aliases: Array<String>,
    internal open val argumentationSyntax: String?,
    internal open val description: String?,
    private val cleanCommandMessage: Boolean = true,
    private val ignoreWhitelist: Boolean = false,
    protected open val userBlockService: UserBlockService? = null,
    internal open vararg val requiredPermissions: Permission
) : ListenerAdapter() {

    companion object {
        const val COMMAND_SIGN = '!'
        private const val ANTI_SPAM_LIMIT = 5.toByte()

        @JvmStatic
        protected val LOG: Logger = LoggerFactory.getLogger(CommandModule::class.java)
        private val SPACE_TRIMMER = "\\s+".toRegex()
        private val antiSpamMap = HashMap<Long, Byte>()
        private var lastAntiSpamCountReset = Instant.now()
    }

    init {
        if (aliases.isEmpty()) {
            throw IllegalArgumentException("Aliases must contain at least one alias.")
        }
    }

    /**
     * When the command is triggered this function will be called
     *
     * @param event The {@code MessageReceivedEvent}
     * @param command The command that was used.
     * @param arguments The arguments that where provided with the command.
     */
    protected abstract fun commandExec(event: MessageReceivedEvent, command: String, arguments: String?)

    /**
     * When a message is received it will decide if the message is a command that should be handled by the command executor.

     * @param event A {@code MessageReceivedEvent}.
     */
    override fun onMessageReceived(event: MessageReceivedEvent) {
        val messageContent = event.message.contentRaw.trim().replace(SPACE_TRIMMER, " ")

        val authorId = event.author.idLong
        if (event.author.isBot || messageContent == "" || event.jda.registeredListeners.stream()
                .anyMatch { it is Sequence && it.user == event.author }
        ) {
            return
        }

        if (messageContent[0] == COMMAND_SIGN) {
            if (userBlockService?.isBlocked(authorId) == true) {
                return
            }
            val argumentsArray = messageContent.split(" ")//.dropLastWhile { it.isEmpty() }.toTypedArray()
            val command = argumentsArray[0].substring(1)
            val arguments: String?
            arguments = if (event.message.contentDisplay.length - 1 >= command.length + 2) {
                messageContent.substring(command.length + 2)
            } else {
                null
            }
            if (Arrays.stream(aliases).anyMatch { s -> s.equals(command, ignoreCase = true) }) {
                try {
                    if (event.isFromType(ChannelType.TEXT) && !ignoreWhitelist) {
                        val commandTextChannelsWhitelist =
                            event.jda.registeredListeners.find { it is CommandTextChannelsWhitelist } as CommandTextChannelsWhitelist?
                        if (commandTextChannelsWhitelist?.isWhitelisted(event.textChannel) == false) {
                            throw IllegalTextChannelException()
                        }
                    }
                    if (requiredPermissions.isNotEmpty()) {
                        if (
                            event.isFromType(ChannelType.TEXT) &&
                            event.member?.permissions?.containsAll(requiredPermissions.asList()) != true
                        ) {
                            throw PermissionException("You do not have sufficient permissions to use this command.\nYou need the following permissions: ${requiredPermissions.contentToString()}")
                        } else if (!event.isFromType(ChannelType.TEXT)) {
                            throw PermissionException("This command requires permissions, which means it has to be executed from a text channel on a guild/server.")
                        }
                    }
                    commandExec(event, command, arguments)
                    LOG.info("Bot ${event.jda.selfUser} on channel ${if (event.channelType == ChannelType.TEXT) "${event.guild} " else ""}${event.channel.name} completed executing ${event.message.contentStripped} command from user ${event.author}")
                } catch (pe: PermissionException) {
                    LOG.warn("Bot ${event.jda.selfUser} on channel ${if (event.channelType == ChannelType.TEXT) "${event.guild} " else ""}${event.channel.name} failed executing ${event.message.contentStripped} command from user ${event.author}")
                    val exceptionMessage =
                        MessageBuilder().append("${event.author.asMention} Cannot complete action due to a permission issue; see the message below for details.")
                            .appendCodeBlock(pe.javaClass.simpleName + ": " + pe.message, "text").build()
                    event.channel.sendMessage(exceptionMessage).queue { it.delete().queueAfter(5, TimeUnit.MINUTES) }
                } catch (t: Throwable) {
                    LOG.error(
                        "Bot " + event.jda.selfUser.toString() + " on channel " + (if (event.channelType == ChannelType.TEXT) event.guild.toString() + " " else "") + event.channel.name + " failed executing " + event.message.contentStripped + " command from user " + event.author.toString(),
                        t
                    )
                    val exceptionMessage =
                        MessageBuilder().append("${event.author.asMention} Cannot complete action due to an error; see the message below for details.")
                            .appendCodeBlock(t.javaClass.simpleName + ": " + t.message, "text").build()
                    event.channel.sendMessage(exceptionMessage).queue { it.delete().queueAfter(5, TimeUnit.MINUTES) }
                }

                try {
                    if (cleanCommandMessage && event.isFromType(ChannelType.TEXT)) {
                        event.message.delete().queue()
                    }
                } catch (e: Exception) {
                    LOG.warn(
                        "Bot ${event.jda.selfUser} on channel ${if (event.channelType == ChannelType.TEXT) "${event.guild} " else ""}${event.channel.name} failed deleting ${event.message.contentStripped} command from user ${event.author}",
                        e
                    )
                }
                spamCheck(event.author)
            }
        }
    }

    protected open fun spamCheck(user: User) {
        if (userBlockService != null) {
            val userId = user.idLong
            when {
                Duration.between(lastAntiSpamCountReset, Instant.now()).seconds > 10 -> {
                    antiSpamMap.clear()
                    lastAntiSpamCountReset = Instant.now()
                }
                antiSpamMap.containsKey(userId) -> {
                    var value = antiSpamMap[userId]!!
                    if (value > ANTI_SPAM_LIMIT) {
                        userBlockService?.blockUser(user)
                    }
                    antiSpamMap[userId] = ++value
                }
                else -> antiSpamMap[userId] = 1
            }
        }
    }

    class IllegalTextChannelException : RuntimeException("You are not allowed to execute commands in this channel.")
}
