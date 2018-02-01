package be.duncanc.discordmodbot.services

import be.duncanc.discordmodbot.commands.CommandModule
import net.dv8tion.jda.core.Permission
import net.dv8tion.jda.core.events.message.MessageReceivedEvent
import net.dv8tion.jda.core.exceptions.PermissionException
import org.json.JSONObject
import java.nio.file.Files
import java.nio.file.Paths
import java.time.OffsetDateTime
import java.util.*
import kotlin.collections.ArrayList
import kotlin.collections.HashMap

object ModNotes : CommandModule(arrayOf("AddNote"), "[user mention] [note text~]", "This command adds a note to the mentioned user for internal use.", requiredPermissions = *arrayOf(Permission.MANAGE_ROLES)) {
    private val FILE_PATH = Paths.get("ModNotes.json")

    private val notes = HashMap<Long, HashMap<Long, ArrayList<Note>>>() //First map long are guild ids, second map long are user ids

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
        if(arguments == null) {
            throw IllegalArgumentException("Arguments are required!")
        }

        val noteText: String
        try {
            noteText = arguments.substring(arguments.split(" ".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()[0].length + 1)
        } catch (e: IndexOutOfBoundsException) {
            throw IllegalArgumentException("No note text provided to add.")
        }

        val toAddNote = event.guild.getMember(event.message.mentionedUsers[0])
        if (!event.member.canInteract(toAddNote)) {
            throw PermissionException("You can't interact with this member")
        }
        addNote(noteText, NoteType.NORMAL, toAddNote.user.idLong, event.guild.idLong)
    }

    private fun addNote(note: String, type: NoteType, userId: Long, guildId: Long, creationDate: OffsetDateTime = OffsetDateTime.now()) {
        val guildNotes = notes[guildId] ?: notes.put(guildId, HashMap())!!
        val userNotes = guildNotes[userId] ?: guildNotes.put(userId,ArrayList())!!
        userNotes.add(Note(note, type, creationDate))
        saveToFile()
    }

    class Note(note: String, val type: NoteType, creationDate: OffsetDateTime = OffsetDateTime.now())
}