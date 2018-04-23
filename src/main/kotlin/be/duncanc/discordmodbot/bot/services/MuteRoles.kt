package be.duncanc.discordmodbot.bot.services

import be.duncanc.discordmodbot.bot.commands.CommandModule
import net.dv8tion.jda.core.Permission
import net.dv8tion.jda.core.entities.Guild
import net.dv8tion.jda.core.entities.Role
import net.dv8tion.jda.core.events.message.MessageReceivedEvent
import net.dv8tion.jda.core.events.role.RoleDeleteEvent
import net.dv8tion.jda.core.exceptions.PermissionException
import org.json.JSONObject
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.*
import java.util.concurrent.TimeUnit

object MuteRoles : CommandModule(arrayOf("MuteRole"), "Name of the mute role or nothing to remove, the role", "This command allows you to set the mute role for a guild/server") {

    private val FILE_PATH: Path = Paths.get("MuteRoles.json")
    private val muteRoles = HashMap<Long, Long>()

    init {
        if (FILE_PATH.toFile().exists()) {
            val stringBuilder = StringBuilder()
            Files.readAllLines(FILE_PATH).forEach { stringBuilder.append(it) }
            val json = JSONObject(stringBuilder.toString())
            json.toMap().forEach { muteRoles.put(it.key.toLong(), it.value as Long) }
        }
    }

    override fun commandExec(event: MessageReceivedEvent, command: String, arguments: String?) {
        if (!event.member.hasPermission(Permission.MANAGE_ROLES)) {
            throw PermissionException("You do not have sufficient permissions to set the mute role for this server")
        }

        val guildId = event.guild.idLong
        if (arguments == null) {
            muteRoles.remove(guildId)
            event.channel.sendMessage("Mute role has been removed.").queue { it.delete().queueAfter(1, TimeUnit.MINUTES) }
        } else {
            try {
                muteRoles.put(guildId, event.guild.getRolesByName(arguments, false)[0].idLong)
                event.channel.sendMessage("Role has been set has mute role.").queue { it.delete().queueAfter(1, TimeUnit.MINUTES) }
            } catch (exception: IndexOutOfBoundsException) {
                throw IllegalArgumentException("Couldn't find any roles with that name.", exception)
            }
        }
        saveMuteRoles()
    }

    private fun saveMuteRoles() {
        val json = JSONObject(muteRoles)
        synchronized(FILE_PATH) {
            Files.write(FILE_PATH, Collections.singletonList(json.toString()))
        }
    }

    override fun onRoleDelete(event: RoleDeleteEvent) {
        muteRoles.remove(event.role.idLong)
        saveMuteRoles()
    }

    fun getMuteRole(guild: Guild): Role {
        try {
            val roleId = muteRoles[guild.idLong]!!

            return guild.getRoleById(roleId)
        } catch (npe: NullPointerException) {
            throw IllegalStateException("This guild does not have a mute role set up.", npe)
        }
    }
}