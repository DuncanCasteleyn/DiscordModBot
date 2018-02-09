package be.duncanc.discordmodbot.services

import be.duncanc.discordmodbot.commands.CommandModule
import net.dv8tion.jda.core.MessageBuilder
import net.dv8tion.jda.core.Permission
import net.dv8tion.jda.core.events.ReadyEvent
import net.dv8tion.jda.core.events.message.MessageReceivedEvent
import net.dv8tion.jda.core.exceptions.PermissionException
import org.json.JSONArray
import org.json.JSONObject
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Paths
import java.time.OffsetDateTime
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.collections.HashMap
import kotlin.collections.HashSet

object ModNotes : CommandModule(arrayOf("AddNote"), "[user mention] [note text~]", "This command adds a note to the mentioned user for internal use.", requiredPermissions = *arrayOf(Permission.MANAGE_ROLES)) {
    private val FILE_PATH = Paths.get("ModNotes.json")
    private val LOG = LoggerFactory.getLogger(this::class.java)

    private val notes = HashMap<Long, HashMap<Long, HashSet<Note>>>() //First map long are guild ids, second map long are user ids

    public enum class NoteType {
        NORMAL, WARN, MUTE, KICK
    }

    init {
        try {
            loadFromFile()
        } catch (e: Exception) {
            LOG.warn("Loading saved notes failed", e)
        }
    }

    private fun saveToFile() {
        val json = JSONObject()
        synchronized(notes) {
            notes.forEach { guildHashMap ->
                val users = JSONObject()
                guildHashMap.value.forEach { userHashMap ->
                    val notes = JSONArray()
                    userHashMap.value.forEach {
                        val note = JSONObject()
                        note.put("note", it.note)
                        note.put("authorId", it.authorId)
                        note.put("creationDate", it.creationDate)
                        note.put("type", it.type.toString())
                        notes.put(note)
                    }
                    users.put(userHashMap.key.toString(), notes)
                }
                json.put(guildHashMap.key.toString(), users)
            }
        }
        Files.write(FILE_PATH, Collections.singletonList(json.toString()))
    }

    private fun loadFromFile() {
        val fileContent = Files.readAllLines(FILE_PATH)
        val stringBuilder = StringBuilder()
        fileContent.forEach {
            stringBuilder.append(it)
        }
        val json = JSONObject(stringBuilder.toString())
        val loadedNotes = HashMap<Long, HashMap<Long, HashSet<Note>>>() //First map long are guild ids, second map long are user ids
        json.toMap().forEach { guildHashMap ->
            val userNotes = HashMap<Long, HashSet<Note>>()
            (guildHashMap.value as Map<*, *>).forEach { userMap ->
                val notes = HashSet<Note>()
                (userMap.value as List<*>).forEach { note ->
                    note as Map<*, *>
                    val noteObject = Note(note["note"] as String, NoteType.valueOf(note["type"] as String), note["authorId"] as Long, OffsetDateTime.parse(note["creationDate"] as String))
                    notes.add(noteObject)
                }
                userNotes[(userMap.key as String).toLong()] = notes
            }
            loadedNotes[(guildHashMap.key as String).toLong()] = userNotes
        }
        synchronized(notes) {
            notes.putAll(loadedNotes)
        }
    }

    override fun commandExec(event: MessageReceivedEvent, command: String, arguments: String?) {
        if (arguments == null) {
            throw IllegalArgumentException("Arguments are required")
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
        synchronized(notes) {
            val guildNotes = if (notes[guildId] == null) {
                val newHashMap = HashMap<Long, HashSet<Note>>()
                notes[guildId] = newHashMap
                newHashMap
            } else {
                notes[guildId]!!
            }
            val userNotes = if (guildNotes[userId] == null) {
                val newHashSet = HashSet<Note>()
                guildNotes[userId] = newHashSet
                newHashSet
            } else {
                guildNotes[userId]!!
            }
            userNotes.add(Note(note, type, authorId, creationDate))
            saveToFile()
        }
    }

    private fun getNotes(userId: Long, guildId: Long): Set<Note> {
        val guildNotes = notes[guildId]
                ?: throw IllegalStateException("This guild does not have any notes saved on users yet.")
        val userNotes = guildNotes[userId] ?: throw IllegalStateException("This user does not have any notes yet.")
        return Collections.unmodifiableSet(userNotes)
    }

    override fun onReady(event: ReadyEvent) {
        event.jda.addEventListener(ViewNotes)
    }

    class Note(val note: String, val type: NoteType, val authorId: Long, val creationDate: OffsetDateTime = OffsetDateTime.now()) {
        override fun toString(): String {
            return "${type.toString().toLowerCase().capitalize()} note\n\nnote: $note\nCreated: $creationDate\nBy: $authorId"
        }
    }

    object ViewNotes : CommandModule(arrayOf("ViewNotes"), "[user mention]", "This command show all the notes on a user. This will send to you by DM due to privacy reasons.", requiredPermissions = *arrayOf(Permission.MANAGE_ROLES)) {
        override fun commandExec(event: MessageReceivedEvent, command: String, arguments: String?) {
            synchronized(notes) {
                try {
                    val userNotes = getNotes((event.message.mentionedUsers[0].idLong), event.guild.idLong)
                    event.author.openPrivateChannel().queue {
                        val messageBuilder = MessageBuilder()
                        userNotes.forEach { messageBuilder.append(it.toString()).append('\n') }
                        messageBuilder.buildAll(MessageBuilder.SplitPolicy.NEWLINE).forEach { message ->
                            it.sendMessage(message).queue { it.delete().queueAfter(15, TimeUnit.MINUTES) }
                        }
                    }
                } catch (ie: IndexOutOfBoundsException) {
                    throw IllegalArgumentException("You need to mention a user", ie)
                }
            }
        }
    }
}