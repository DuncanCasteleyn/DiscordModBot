package be.duncanc.discordmodbot.services

import be.duncanc.discordmodbot.commands.CommandModule
import net.dv8tion.jda.core.events.message.MessageReceivedEvent
import java.nio.file.Paths

object ModNotes : CommandModule(arrayOf("AddNote"), "[user mention] [note text~]", "This command adds a note to the mentioned user for internal use.") {
    private val FILE_PATH = Paths.get("ModNotes.json")

    private val notes = HashMap<Long, HashMap<Long, String>>()


    override fun commandExec(event: MessageReceivedEvent, command: String, arguments: String?) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }
}