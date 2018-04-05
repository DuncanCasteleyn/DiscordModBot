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

package be.duncanc.discordmodbot.services

import be.duncanc.discordmodbot.commands.CommandModule
import be.duncanc.discordmodbot.commands.Help
import be.duncanc.discordmodbot.sequences.Sequence
import net.dv8tion.jda.core.MessageBuilder
import net.dv8tion.jda.core.Permission
import net.dv8tion.jda.core.entities.MessageChannel
import net.dv8tion.jda.core.entities.User
import net.dv8tion.jda.core.events.ReadyEvent
import net.dv8tion.jda.core.events.message.MessageReceivedEvent
import org.json.JSONObject
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.NoSuchFileException
import java.nio.file.Paths
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.collections.ArrayList
import kotlin.collections.HashMap

open class EventsManager : CommandModule(EVENTS_LIST_ALIASES, null, EVENTS_LIST_DESCRIPTION) {
    lateinit var events: HashMap<Long, ArrayList<Event>>

    companion object {
        const private val EVENTS_LIST_DESCRIPTION = "Shows a list with currently planned events."
        const private val EVENT_MANAGER_DESCRIPTION = "Allows you to manage events."
        private val EVENTS_LIST_ALIASES = arrayOf("EventsList")
        private val EVENT_MANAGER_ALIASES = arrayOf("EventManager", "ManageEvents")
        private val FILE_PATH = Paths.get("Events.json")
        private val LOG = LoggerFactory.getLogger(EventsManager::class.java)
        private val DATE_TIME_FORMATTER_PARSER = DateTimeFormatter.ofPattern("d-M-yyyy H:mm X", Locale.ENGLISH)
        private val DATE_TIME_FORMATTER_LIST = DateTimeFormatter.ofPattern("E dd-MM-yyyy HH:mm", Locale.ENGLISH)
    }

    override fun onReady(event: ReadyEvent) {
        event.jda.addEventListener(EventManagerCommand())
        readEventsFromFile()
        cleanExpiredEvents()
    }

    override fun commandExec(event: MessageReceivedEvent, command: String, arguments: String?) {
        try {
            val messageBuilder = MessageBuilder()
            val events = events[event.guild.idLong]
            cleanExpiredEvents()
            messageBuilder.append("Current UTC time is ").append(OffsetDateTime.now().atZoneSameInstant(ZoneOffset.UTC).format(DATE_TIME_FORMATTER_LIST)).append("\n\nEvents list (all times are UTC):\n\n")
            events!!.map { messageBuilder.append("``").append(it.toString()).append("``\n") }
            messageBuilder.buildAll(MessageBuilder.SplitPolicy.NEWLINE).forEach { event.channel.sendMessage(it).queue { it.delete().queueAfter(2, TimeUnit.MINUTES) } }
        } catch (npe: NullPointerException) {
            throw UnsupportedOperationException("This guild has not been configured to use the event manager.", npe)
        }
    }

    fun writeEventsToFile() {
        val json = JSONObject()
        events.map { json.put(it.key.toString(), it.value) }
        Files.write(FILE_PATH, Collections.singletonList(json.toString()))
    }

    fun readEventsFromFile() {
        try {
            val stringBuilder = StringBuilder()
            Files.readAllLines(FILE_PATH).map { stringBuilder.append(it) }
            val json = JSONObject(stringBuilder.toString())
            events = HashMap()
            json.toMap().forEach {
                val value = it.value
                val arrayList = ArrayList<Event>()
                try {
                    if (value is ArrayList<*>) {
                        value.forEach {
                            if (it is HashMap<*, *>) {
                                val event = Event(it["eventName"] as String)
                                event.eventDateTime = OffsetDateTime.parse(it["eventDateTime"] as String)
                                arrayList.add(event)
                            } else {
                                throw IllegalStateException("Unexpected type " + it.javaClass.typeName + " after mapping JSON.")
                            }
                        }
                    } else {
                        throw IllegalStateException("Unexpected type " + it.javaClass.typeName + " after mapping JSON.")
                    }
                } catch (ise: IllegalStateException) {
                    LOG.error("Reading config failed", ise)
                }
                events.put(it.key.toLong(), arrayList)
            }
        } catch (e: NoSuchFileException) {
            events = HashMap()
        }
    }

    fun cleanExpiredEvents() {
        events.forEach {
            val arrayList = it.value
            arrayList.removeIf { it.eventDateTime.isBefore(OffsetDateTime.now()) }
        }
    }

    class Event(val eventName: String) {
        lateinit var eventDateTime: OffsetDateTime

        override fun toString(): String {
            val dateTimeFormatted = eventDateTime.format(DATE_TIME_FORMATTER_LIST)
            return String.format("%-25s%s", eventName, dateTimeFormatted)
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

    inner class EventMangerSequence(user: User, channel: MessageChannel, cleanAfterSequence: Boolean = true, informUser: Boolean = true) : Sequence(user, channel, cleanAfterSequence, informUser) {
        private var sequenceNumber: Byte = 0
        private lateinit var eventName: String

        init {
            channel.sendMessage(user.asMention + " Would you like to add or remove an event? Please answer with \"add\" or \"remove\".").queue { addMessageToCleaner(it) }
        }

        override fun onMessageReceivedDuringSequence(event: MessageReceivedEvent) {
            when (sequenceNumber) {
                0.toByte() -> {
                    when (event.message.contentRaw) {
                        "add" -> {
                            sequenceNumber = 1
                            event.channel.sendMessage("Please enter the event name.").queue { super.addMessageToCleaner(it) }
                        }
                        "remove" -> {
                            val messageBuilder = MessageBuilder()
                            try {
                                events[event.guild.idLong]!!.map { messageBuilder.append("``").append(it.toString()).append("``\n") }
                            } catch (npe: NullPointerException) {
                                throw UnsupportedOperationException("This guild has not been configured to use the event manager.", npe)
                            }
                            messageBuilder.buildAll(MessageBuilder.SplitPolicy.NEWLINE).forEach { super.channel.sendMessage(it).queue() }
                            super.channel.sendMessage("Enter the event name you want to remove.").queue { addMessageToCleaner(it) }
                            sequenceNumber = 3
                        }
                        else -> {
                            super.channel.sendMessage("Wrong answer. Please answer with \"add\" or \"remove\"").queue { super.addMessageToCleaner(it) }
                        }
                    }
                }
                1.toByte() -> {
                    sequenceNumber = 2
                    val guildId = event.guild.idLong
                    if (!events.containsKey(guildId)) {
                        events.put(event.guild.idLong, java.util.ArrayList())
                    }
                    events[event.guild.idLong]!!.forEach {
                        if (it.eventName == event.message.contentRaw) {
                            throw IllegalArgumentException("An event with this name already exists, please try again and chose another name.")
                        }
                    }
                    eventName = event.message.contentRaw
                    event.channel.sendMessage("Please enter the date and time of the event. Example: \"12-08-2018 12:00 +02\"").queue { addMessageToCleaner(it) }
                }
                2.toByte() -> {
                    val scheduledEvent = Event(eventName)
                    try {
                        scheduledEvent.eventDateTime = OffsetDateTime.parse(event.message.contentDisplay, DATE_TIME_FORMATTER_PARSER).atZoneSameInstant(ZoneOffset.UTC).toOffsetDateTime()
                        events[event.guild.idLong]!!.add(scheduledEvent)
                        writeEventsToFile()
                        destroy()
                        super.channel.sendMessage("Event successfully added").queue { it.delete().queueAfter(1, TimeUnit.MINUTES) }
                    } catch (exception: DateTimeParseException) {
                        event.channel.sendMessage(exception.javaClass.simpleName + ": " + exception.message).queue { addMessageToCleaner(it) }
                    }
                }
                3.toByte() -> {
                    val searchTerm = event.message.contentRaw
                    val matches = events[event.guild.idLong]!!.filter { it.eventName == searchTerm }
                    if (matches.isEmpty()) {
                        super.channel.sendMessage("Could not find any events with that name.").queue { it.delete().queueAfter(1, TimeUnit.MINUTES) }
                    } else {
                        matches.forEach { events[event.guild.idLong]!!.remove(it) }
                        writeEventsToFile()
                        super.channel.sendMessage("Event with name \"$searchTerm\" has been deleted").queue { it.delete().queueAfter(1, TimeUnit.MINUTES) }
                        super.destroy()
                    }
                }
            }
        }
    }
}