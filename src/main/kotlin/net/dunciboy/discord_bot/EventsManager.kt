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
import net.dv8tion.jda.core.MessageBuilder
import net.dv8tion.jda.core.Permission
import net.dv8tion.jda.core.entities.MessageChannel
import net.dv8tion.jda.core.entities.User
import net.dv8tion.jda.core.events.ReadyEvent
import net.dv8tion.jda.core.events.message.MessageReceivedEvent
import net.dv8tion.jda.core.hooks.ListenerAdapter
import org.json.JSONObject
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.time.OffsetDateTime
import java.time.format.DateTimeParseException
import java.util.*
import java.util.concurrent.TimeUnit

@Suppress("unused") // still under development. <- todo remove when done.
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
        TODO("Not implemented. Need to Init HashMap by retrieve existing events from a JSON file, then filter expired events if needed and remove empty HashMap values")
    }

    fun writeEventsToFile() {
        val json = JSONObject()
        events.map { json.put(it.key.toString(), it.value) }
        Files.write(FILE_PATH, Collections.singletonList(json.toString()))
    }

    class Event(val eventName: String) {
        lateinit var eventDateTime: OffsetDateTime
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

    inner class EventMangerSequence(user: User, channel: MessageChannel, cleanAfterSequence: Boolean = true, informUser: Boolean = true) : Sequence(user, channel, cleanAfterSequence, informUser) {
        private var sequenceNumber: Byte = 0
        private lateinit var eventName: String

        init {
            channel.sendMessage(user.asMention + " Would you like to add or remove an event? Please answer with \"add\" or \"remove\".")
        }

        override fun onMessageReceivedDuringSequence(event: MessageReceivedEvent) {
            when (sequenceNumber) {
                0.toByte() -> {
                    when (event.message.rawContent) {
                        "add" -> {
                            sequenceNumber = 1
                            event.channel.sendMessage("Please enter the event name.").queue { super.addMessageToCleaner(it) }
                        }
                        "remove" -> {
                            val messageBuilder = MessageBuilder()
                            try {
                                events[event.guild.idLong]!!.map { messageBuilder.append(it.eventName).append('\t').append(it.eventDateTime.toString()).append('\n') }
                            } catch (npe: NullPointerException) {
                                throw UnsupportedOperationException("This guild has not been configured to use the event manager.", npe)
                            }
                            messageBuilder.buildAll(MessageBuilder.SplitPolicy.NEWLINE).forEach { super.channel.sendMessage(it).queue() }
                            super.channel.sendMessage("Enter the event name you want to remove.")
                            sequenceNumber = 3
                        }
                        else -> {
                            super.channel.sendMessage("Wrong answer. Please answer with \"add\" or \"remove\"").queue { super.addMessageToCleaner(it) }
                        }
                    }
                }
                1.toByte() -> {
                    sequenceNumber = 2
                    eventName = event.message.content
                    event.channel.sendMessage("Please enter the date and time of the event.")
                }
                2.toByte() -> {
                    val scheduledEvent = Event(eventName)
                    try {
                        scheduledEvent.eventDateTime = OffsetDateTime.parse(event.message.content)
                        writeEventsToFile()
                        destroy()
                        super.channel.sendMessage("Event successfully added").queue { it.delete().queueAfter(1, TimeUnit.MINUTES) }
                    } catch (exception: DateTimeParseException) {
                        event.channel.sendMessage(exception.javaClass.simpleName + ": " + exception.message)
                        return
                    }
                    try {
                        events[event.guild.idLong]!!.add(scheduledEvent)
                    } catch (npe: NullPointerException) {
                        throw UnsupportedOperationException("This guild has not been configured to use the event manager.", npe)
                    }
                }
                3.toByte() -> {
                    val matches = events[event.guild.idLong]?.filter { it.eventName == event.message.rawContent }
                    if (matches == null) {
                        super.channel.sendMessage("Could not find any events with that name.").queue()
                        super.destroy()
                    } else {
                        matches.forEach { events[event.guild.idLong]?.remove(it) }
                    }
                }
            }
        }
    }

    inner class EventsList : CommandModule(EVENTS_LIST_ALIASES, null, EVENTS_LIST_DESCRIPTION) {

        override fun commandExec(event: MessageReceivedEvent, command: String, arguments: String?) {
            val messageBuilder = MessageBuilder()
            try {
                val events = events[event.guild.idLong]
                events!!.filter { it.eventDateTime.isBefore(OffsetDateTime.now()) }.forEach { events.remove(it) }
                events.map { messageBuilder.append(it.toString()).append('\n') }
            } catch (npe: NullPointerException) {
                throw UnsupportedOperationException("This guild has not been configured to use the event manager.", npe)
            }
            messageBuilder.buildAll(MessageBuilder.SplitPolicy.NEWLINE).forEach { event.channel.sendMessage(it).queue() }
        }
    }
}