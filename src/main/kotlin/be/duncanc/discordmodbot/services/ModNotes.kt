package be.duncanc.discordmodbot.services

import be.duncanc.discordmodbot.commands.CommandModule
import net.dv8tion.jda.core.events.message.MessageReceivedEvent
import org.json.JSONObject
import java.nio.file.Files
import java.nio.file.Paths
import java.time.OffsetDateTime
import java.util.*
import kotlin.collections.ArrayList

object ModNotes : CommandModule(arrayOf("AddNote"), "[user mention] [note text~]", "This command adds a note to the mentioned user for internal use.") {
    private val FILE_PATH = Paths.get("ModNotes.json")

    private val notes = HashMap<Long, ArrayList<Notes>>() //todo think about saving this per user.

    enum class NoteType {
        NORMAL, WARN, MUTE, KICK
    }

    private fun saveToFile() {
        val json = JSONObject(notes)
        Files.write(FILE_PATH, Collections.singletonList(json.toString()))
    }

    private fun loadFromFile() {
        val fileContent = Files.readAllLines(FILE_PATH)
        val stringBuilder = StringBuilder()
        fileContent.forEach {
            stringBuilder.append(it)
        }
        val json = JSONObject(stringBuilder.toString())
        TODO()
    }

    override fun commandExec(event: MessageReceivedEvent, command: String, arguments: String?) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    private fun addNote(note: String, type: NoteType) {
        TODO()
    }

    class Note(note: String, val type: NoteType, creationDate: OffsetDateTime = OffsetDateTime.now())

    class Notes(val userId: Long) {
        val notes = ArrayList<Note>()
    }
}