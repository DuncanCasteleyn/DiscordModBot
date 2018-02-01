package be.duncanc.discordmodbot.services

import be.duncanc.discordmodbot.commands.CommandModule
import net.dv8tion.jda.core.MessageBuilder
import net.dv8tion.jda.core.Permission
import net.dv8tion.jda.core.events.ReadyEvent
import net.dv8tion.jda.core.events.message.MessageReceivedEvent
import net.dv8tion.jda.core.exceptions.PermissionException
import org.json.JSONObject
import java.nio.file.Files
import java.nio.file.Paths
import java.time.OffsetDateTime
import java.util.*
import java.util.concurrent.TimeUnit
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
        json.toMap().forEach { guildHashMap ->
            (guildHashMap.value as HashMap<*, *>).forEach { userHashMap ->
                val notes = ArrayList<Note>()
                (userHashMap.value as Array<*>).forEach { note ->
                    TODO(note.toString())
                }
            }
        }
    }

    override fun commandExec(event: MessageReceivedEvent, command: String, arguments: String?) {
        if (arguments == null) {
            throw IllegalArgumentException("Arguments are required!")
        }

        val noteText: String
        try {
            noteText = arguments.substring(arguments.split(" ").toTypedArray()[0].length + 1)
        } catch (e: IndexOutOfBoundsException) {
            throw IllegalArgumentException("No note text provided to add.")
        }

        val toAddNote = event.guild.getMember(event.message.mentionedUsers[0])
        if (!event.member.canInteract(toAddNote)) {
            throw PermissionException("You can't interact with this member")
        }
        addNote(noteText, NoteType.NORMAL, toAddNote.user.idLong, event.guild.idLong, event.author.idLong)
    }

    private fun addNote(note: String, type: NoteType, userId: Long, guildId: Long, authorId: Long, creationDate: OffsetDateTime = OffsetDateTime.now()) {
        val guildNotes = notes[guildId] ?: notes.put(guildId, HashMap())!!
        val userNotes = guildNotes[userId] ?: guildNotes.put(userId, ArrayList())!!
        userNotes.add(Note(note, type, authorId, creationDate))
        saveToFile()
    }

    private fun getNotes(userId: Long, guildId: Long): List<Note> {
        val guildNotes = notes[guildId]
                ?: throw IllegalStateException("This guild does not have any notes saved on users yet.")
        val userNotes = guildNotes[userId] ?: throw IllegalStateException("This user does not have any notes yet.")
        return Collections.unmodifiableList(userNotes)
    }

    override fun onReady(event: ReadyEvent) {
        event.jda.addEventListener(ViewNotes)
    }

    class Note(private val note: String, private val type: NoteType, private val authorId: Long, private val creationDate: OffsetDateTime = OffsetDateTime.now()) {
        override fun toString(): String {
            return "$type note\nnote: $note\n\nCreated: $creationDate\nBy: $authorId)"
        }
    }

    object ViewNotes : CommandModule(arrayOf("ViewNotes"), "[user mention]", "This command show all the notes on a user. This will send to you by DM due to privacy reasons.", requiredPermissions = *arrayOf(Permission.MANAGE_ROLES)) {
        override fun commandExec(event: MessageReceivedEvent, command: String, arguments: String?) {
            val userNotes = getNotes((event.message.mentionedUsers[0].idLong), event.guild.idLong)
            event.author.openPrivateChannel().queue {
                val messageBuilder = MessageBuilder()
                userNotes.forEach { messageBuilder.append(it.toString()).append('\n') }
                messageBuilder.buildAll(MessageBuilder.SplitPolicy.NEWLINE).forEach { message ->
                    it.sendMessage(message).queue { it.delete().queueAfter(15, TimeUnit.MINUTES) }
                }
            }
        }
    }
}