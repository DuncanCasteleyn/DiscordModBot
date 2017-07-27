package net.dunciboy.discord_bot

import net.dunciboy.discord_bot.commands.CommandModule
import net.dunciboy.discord_bot.sequence.Sequence
import net.dv8tion.jda.core.entities.MessageChannel
import net.dv8tion.jda.core.entities.User
import net.dv8tion.jda.core.events.ReadyEvent
import net.dv8tion.jda.core.events.message.MessageReceivedEvent
import net.dv8tion.jda.core.hooks.ListenerAdapter
import java.nio.file.Path
import java.nio.file.Paths
import java.time.OffsetDateTime
import java.util.*

class EventsManager : ListenerAdapter() {
    private lateinit var events: HashMap<Long, ArrayList<Event>>

    companion object {
        const private val EVENTS_LIST_DESCRIPTION = "Shows a list with currently planned events"
        private val EVENTS_LIST_ALIASES: Array<String> = arrayOf("EventsList")
        private val FILE_PATH: Path = Paths.get("Events.json")
    }

    override fun onReady(event: ReadyEvent?) {
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

    class EventManagerCommand : CommandModule(ALIASES, null, DESCRIPTION) {
        companion object {
            private val ALIASES: Array<String> = arrayOf("EventManager", "ManageEvents")
            const private val DESCRIPTION: String = "Allows you to manage events."
        }

        override fun commandExec(event: MessageReceivedEvent, command: String, arguments: String?) {
            TODO("not implemented")
        }
    }

    inner class EventMangerSequence(user: User, channel: MessageChannel, cleanAfterSequence: Boolean = true, informUser: Boolean = true) : Sequence(user, channel, cleanAfterSequence, informUser) {
        override fun onMessageReceivedDuringSequence(event: MessageReceivedEvent) {
            TODO("not implemented")
        }

    }

    inner class EventsList : CommandModule(EVENTS_LIST_ALIASES, null, EVENTS_LIST_DESCRIPTION) {

        override fun commandExec(event: MessageReceivedEvent, command: String, arguments: String?) {
            TODO("not implemented")
        }
    }
}