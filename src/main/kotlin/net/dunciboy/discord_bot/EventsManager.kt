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
import net.dv8tion.jda.core.Permission
import net.dv8tion.jda.core.entities.MessageChannel
import net.dv8tion.jda.core.entities.User
import net.dv8tion.jda.core.events.ReadyEvent
import net.dv8tion.jda.core.events.message.MessageReceivedEvent
import net.dv8tion.jda.core.hooks.ListenerAdapter
import java.nio.file.Path
import java.nio.file.Paths
import java.time.OffsetDateTime
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.collections.ArrayList

class EventsManager : ListenerAdapter() {
    private lateinit var events: HashMap<Long, ArrayList<Event>>

    companion object {
        const private val EVENTS_LIST_DESCRIPTION = "Shows a list with currently planned events"
        const private val EVENT_MANAGER_DESCRIPTION: String = "Allows you to manage events."
        private val EVENTS_LIST_ALIASES: Array<String> = arrayOf("EventsList")
        private val EVENT_MANAGER_ALIASES: Array<String> = arrayOf("EventManager", "ManageEvents")
        private val FILE_PATH: Path = Paths.get("Events.json")
    }

    override fun onReady(event: ReadyEvent) {
        val guildIds: ArrayList<Long> = ArrayList()
        event.jda.guilds.map { guildIds.add(it.idLong) }
        TODO("Not implemented. Need to Init HashMap by retrieving all guild ids and then retrieve existing events from a JSON file, then filter expired events if needed and remove empty HashMap values")
    }

    fun writeEventsToFile() {
        TODO("Write files not implemented")
    }

    class Event(val eventName: String, eventDateTime: OffsetDateTime) {
        var eventDateTime: OffsetDateTime = eventDateTime
            private set

        fun setDateTime(dateTimeString: String) {
            eventDateTime = OffsetDateTime.parse(dateTimeString)
        }
    }

    inner class EventManagerCommand : CommandModule(EVENT_MANAGER_ALIASES, null, EVENT_MANAGER_DESCRIPTION) {

        override fun commandExec(event: MessageReceivedEvent, command: String, arguments: String?) {
            if (event.member.hasPermission(Permission.MANAGE_ROLES)) {
                event.jda.addEventListener(EventMangerSequence(event.author, event.channel))
            } else {
                event.channel.sendMessage("You do not have permission to manage events.").queue { it.delete().queueAfter(1, TimeUnit.MINUTES) }
            }
        }
    }

    inner class EventMangerSequence(user: User, channel: MessageChannel, cleanAfterSequence: Boolean = true, informUser: Boolean = true, private var sequenceNumber: Int = 0) : Sequence(user, channel, cleanAfterSequence, informUser) {
        init {
            channel.sendMessage(user.asMention + " Would you like to add or remove an event? Please answer with \"add\" or \"remove\".")
        }

        override fun onMessageReceivedDuringSequence(event: MessageReceivedEvent) {
            when (sequenceNumber) {
                0 -> {
                    when (event.message.rawContent) {
                        "add" -> {
                            sequenceNumber = 1
                            TODO("Logic to read times not implemented yet.")
                        }
                        "remove" -> {
                            sequenceNumber = 2
                            TODO("Logic to modify events not implemented yet.")
                        }
                        else -> {
                            super.channel.sendMessage("Wrong answer. Please answer with \"add\" or \"remove\"").queue { super.addMessageToCleaner(it) }
                        }
                    }
                }
            }
        }
    }

    inner class EventsList : CommandModule(EVENTS_LIST_ALIASES, null, EVENTS_LIST_DESCRIPTION) {

        override fun commandExec(event: MessageReceivedEvent, command: String, arguments: String?) {
            TODO("Printing of events is not implemented yet.")
        }
    }
}