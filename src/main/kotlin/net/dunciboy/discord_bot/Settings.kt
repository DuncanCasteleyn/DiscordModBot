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

package net.dunciboy.discord_bot

import net.dunciboy.discord_bot.commands.CommandModule
import net.dunciboy.discord_bot.sequence.Sequence
import net.dv8tion.jda.core.entities.MessageChannel
import net.dv8tion.jda.core.entities.User
import net.dv8tion.jda.core.events.message.MessageReceivedEvent
import java.util.concurrent.TimeUnit

/**
 * This allows for settings to be adjusted.
 *
 * @author Duncan
 * @since 1.0.0
 */

//todo remove hard coded code.
internal class Settings(var isLogMessageDelete: Boolean = true, var isLogMessageUpdate: Boolean = true, var isLogMemberRemove: Boolean = true, var isLogMemberBan: Boolean = true, var isLogMemberAdd: Boolean = true, var isLogMemberRemoveBan: Boolean = true) : CommandModule(ALIAS, null, DESCRIPTION) {

    private val exceptedFromLogging = ArrayList<Long>()

    init {
        exceptedFromLogging.add(231422572011585536L)
        exceptedFromLogging.add(205415791238184969L)
        exceptedFromLogging.add(204047108318298112L)
    }

    companion object {
        private val ALIAS = arrayOf("settings")
        private const val DESCRIPTION = "Allows settings to be adjusted"
        private const val OWNER_ID = 159419654148718593L
    }

    fun isExceptedFromLogging(channelId: Long): Boolean {
        return exceptedFromLogging.contains(channelId)
    }

    override fun commandExec(event: MessageReceivedEvent, command: String, arguments: String?) {
        if (event.author.idLong == OWNER_ID) {
            event.jda.addEventListener(SettingsSequence(event.author, event.channel))
        } else {
            event.channel.sendMessage("Sorry, only the owner can use this command right now.").queue { it.delete().queueAfter(1, TimeUnit.MINUTES) }
        }
    }

    inner class SettingsSequence(user: User, channel: MessageChannel) : Sequence(user, channel) {
        private val eventManger: EventsManager
        private var sequenceNumber = 0.toByte()

        init {
            try {
                eventManger = super.channel.jda.registeredListeners.filter { it is EventsManager }[0] as EventsManager
            } catch (e: IndexOutOfBoundsException) {
                destroy()
                throw UnsupportedOperationException("This bot does not have an event manager", e)
            }
            super.channel.sendMessage("What would you like to do? Respond with the number of the action you'd like to perform.\n\n" +
                    "0. Add or remove this guild from the event manager").queue { super.addMessageToCleaner(it) }
        }

        override fun onMessageReceivedDuringSequence(event: MessageReceivedEvent) {
            val guildId = event.guild.idLong

            when (sequenceNumber) {
                0.toByte() -> {
                    when (event.message.rawContent.toByte()) {
                        0.toByte() -> {
                            val response: Array<String> = if (eventManger.events.containsKey(guildId)) {
                                arrayOf("already", "remove")
                            } else {
                                arrayOf("not", "add")
                            }
                            super.channel.sendMessageFormat("The guild is currently %s in the list to use the event manager, do you want to %s it? Respond with \"yes\" or \"no\".", response[0], response[1]).queue { addMessageToCleaner(it) }
                            sequenceNumber = 1
                        }
                    }
                }
                1.toByte() -> {
                    when (event.message.rawContent.toLowerCase()) {
                        "yes" -> {
                            if (eventManger.events.containsKey(guildId)) {
                                eventManger.events.remove(guildId)
                            } else {
                                eventManger.events.put(guildId, ArrayList())
                            }
                            eventManger.cleanExpiredEvents()
                            eventManger.writeEventsToFile()
                            super.channel.sendMessage("Executed without problem.").queue { it.delete().queueAfter(1, TimeUnit.MINUTES) }
                            super.destroy()
                        }
                        else -> {
                            super.destroy()
                        }
                    }
                }
            }
        }
    }
}
