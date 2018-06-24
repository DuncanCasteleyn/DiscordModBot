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

import be.duncanc.discordmodbot.bot.sequences.Sequence
import net.dv8tion.jda.core.JDA
import net.dv8tion.jda.core.MessageBuilder
import net.dv8tion.jda.core.Permission
import net.dv8tion.jda.core.entities.ChannelType
import net.dv8tion.jda.core.entities.Guild
import net.dv8tion.jda.core.entities.Message
import net.dv8tion.jda.core.entities.TextChannel
import net.dv8tion.jda.core.events.ReadyEvent
import net.dv8tion.jda.core.events.message.MessageReceivedEvent
import net.dv8tion.jda.core.exceptions.PermissionException
import net.dv8tion.jda.core.hooks.ListenerAdapter
import org.json.JSONArray
import org.json.JSONObject
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.collections.HashSet

/**
 * an abstract class that can be used to listen for commands.
 *
 * @property aliases             the description for this command
 * @property argumentationSyntax the syntax for the argumentation of the command, put null if none needed.
 * @property description         The description of the command
 * @property cleanCommandMessage Allows you to enable or disable cleaning up commands.
 * @since 1.0.0
 */
abstract class CommandModule @JvmOverloads protected constructor(internal val aliases: Array<String>, internal val argumentationSyntax: String?, internal val description: String?, private val cleanCommandMessage: Boolean = true, private val ignoreWhiteList: Boolean = false, vararg val requiredPermissions: Permission) : ListenerAdapter() {
    companion object {
        const val COMMAND_SIGN = '!'
        @JvmStatic
        protected val LOG: Logger = LoggerFactory.getLogger(CommandModule::class.java)
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
        val messageContent = event.message.contentRaw.trim().replace("\\s+".toRegex(), " ")

        if (event.author.isBot || messageContent == "" || event.jda.registeredListeners.stream().anyMatch { it is Sequence && it.user == event.author }) {
            return
        }

        if (messageContent[0] == COMMAND_SIGN) {
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
                    if (event.isFromType(ChannelType.TEXT) && !ignoreWhiteList && !CommandTextChannelsWhitelist.isWhitelisted(event.textChannel)) {
                        throw CommandTextChannelsWhitelist.IllegalTextChannelException()
                    }
                    if (requiredPermissions.isNotEmpty()) {
                        if (event.isFromType(ChannelType.TEXT) && !event.member.permissions.containsAll(requiredPermissions.asList())) {
                            throw PermissionException("You do not have sufficient permissions to use this command.\nYou need the following permissions: " + requiredPermissions.contentToString())
                        } else if (!event.isFromType(ChannelType.TEXT)) {
                            throw PermissionException("This command requires permissions, which means it has to be executed from a text channel on a guild/server.")
                        }
                    }
                    commandExec(event, command, arguments)
                    LOG.info("Bot " + event.jda.selfUser.toString() + " on channel " + (if (event.guild != null) event.guild.toString() + " " else "") + event.channel.name + " completed executing " + event.message.contentStripped + " command from user " + event.author.toString())
                } catch (pe: PermissionException) {
                    LOG.warn("Bot " + event.jda.selfUser.toString() + " on channel " + (if (event.guild != null) event.guild.toString() + " " else "") + event.channel.name + " failed executing " + event.message.contentStripped + " command from user " + event.author.toString())
                    val exceptionMessage = MessageBuilder().append("Cannot complete action due to a permission issue; see the message below for details.").appendCodeBlock(pe.javaClass.simpleName + ": " + pe.message, "text").build()
                    event.channel.sendMessage(exceptionMessage).queue { it.delete().queueAfter(5, TimeUnit.MINUTES) }
                } catch (t: Throwable) {
                    LOG.error("Bot " + event.jda.selfUser.toString() + " on channel " + (if (event.guild != null) event.guild.toString() + " " else "") + event.channel.name + " failed executing " + event.message.contentStripped + " command from user " + event.author.toString(), t)
                    val exceptionMessage = MessageBuilder().append("Cannot complete action due to an error; see the message below for details.").appendCodeBlock(t.javaClass.simpleName + ": " + t.message, "text").build()
                    event.channel.sendMessage(exceptionMessage).queue { it.delete().queueAfter(5, TimeUnit.MINUTES) }
                }

                try {
                    if (cleanCommandMessage && event.isFromType(ChannelType.TEXT)) {
                        event.message.delete().queue()
                    }
                } catch (e: Exception) {
                    LOG.warn("Bot " + event.jda.selfUser.toString() + " on channel " + (if (event.guild != null) event.guild.toString() + " " else "") + event.channel.name + " failed deleting " + event.message.contentStripped + " command from user " + event.author.toString(), e)
                }
            }
        }
    }

    object CommandTextChannelsWhitelist : CommandModule(arrayOf("CommandWhitelistChannel", "WhitelistChannel"), null, "Whitelists the channel so commands can be used in it.", ignoreWhiteList = true, requiredPermissions = *arrayOf(Permission.MANAGE_CHANNEL)) {
        private val whitelist = HashMap<Guild, HashSet<TextChannel>>()
        private val FILE_PATH: Path = Paths.get("CommandTextChannelsWhitelist.json")

        private fun save() {
            synchronized(whitelist) {
                val jsonObject = JSONObject()
                whitelist.forEach { mapEntry ->
                    val jsonArray = JSONArray()
                    mapEntry.value.forEach { setEntry ->
                        jsonArray.put(setEntry.idLong)
                    }
                    jsonObject.put(mapEntry.key.id, jsonArray)
                }
                synchronized(FILE_PATH) {
                    Files.write(FILE_PATH, Collections.singletonList(jsonObject.toString()))
                }
            }
        }

        private fun load(jda: JDA) {
            if (FILE_PATH.toFile().exists()) {
                synchronized(FILE_PATH) {
                    val stringBuilder = StringBuilder()
                    Files.readAllLines(FILE_PATH).forEach { stringBuilder.append(it) }
                    val jsonObject = JSONObject(stringBuilder.toString())
                    val whitelist = HashMap<Guild, HashSet<TextChannel>>()
                    jsonObject.toMap().forEach { mapK, mapV ->
                        mapV as ArrayList<*>
                        val set = HashSet<TextChannel>()
                        mapV.forEach {
                            val textChannel = jda.getTextChannelById(it as Long)
                            if (textChannel != null && textChannel.guild.id == mapK) {
                                set.add(textChannel)
                            }
                        }
                        val guild = jda.getGuildById(mapK as String)
                        if (guild != null) {
                            whitelist[guild] = set
                        }
                    }
                    synchronized(CommandTextChannelsWhitelist.whitelist) {
                        CommandTextChannelsWhitelist.whitelist.putAll(whitelist)
                    }
                }
            }
        }

        override fun onReady(event: ReadyEvent) {
            load(event.jda)
        }

        override fun commandExec(event: MessageReceivedEvent, command: String, arguments: String?) {
            val hashSet = whitelist[event.guild]
            if (hashSet?.contains(event.textChannel) == true) {
                hashSet.remove(event.textChannel)
                if (hashSet.isEmpty()) {
                    whitelist.remove(event.guild)
                    event.channel.sendMessage("The channel was removed from the whitelist. There are no channels left on the whitelist commands can be used in all channels now.").queue(cleanMessages())
                } else {
                    event.channel.sendMessage("The channel was removed from the whitelist.").queue(cleanMessages())
                }
            } else if (hashSet == null) {
                val newHashSet = HashSet<TextChannel>()
                newHashSet.add(event.textChannel)
                whitelist[event.guild] = newHashSet
                event.channel.sendMessage("The channel was added to the whitelist. Commands can now only be used in whitelisted channels.").queue(cleanMessages())
            } else {
                hashSet.add(event.textChannel)
                event.channel.sendMessage("The channel was added to the whitelist.").queue(cleanMessages())
            }
            save()
        }

        private fun cleanMessages(): (Message) -> Unit =
                { it.delete().queueAfter(1, TimeUnit.MINUTES) }

        fun isWhitelisted(textChannel: TextChannel): Boolean {
            val contains = whitelist[textChannel.guild]?.contains(textChannel)
            return contains == true || contains == null || whitelist.isEmpty()
        }

        class IllegalTextChannelException : RuntimeException("You are not allowed to execute commands in this channel.")
    }
}
