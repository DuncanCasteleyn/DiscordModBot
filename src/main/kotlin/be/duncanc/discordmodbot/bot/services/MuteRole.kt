/*
 * Copyright 2018 Duncan Casteleyn
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package be.duncanc.discordmodbot.bot.services

import be.duncanc.discordmodbot.bot.commands.CommandModule
import be.duncanc.discordmodbot.data.entities.MuteRole
import be.duncanc.discordmodbot.data.repositories.MuteRolesRepository
import net.dv8tion.jda.core.Permission
import net.dv8tion.jda.core.entities.Guild
import net.dv8tion.jda.core.entities.Role
import net.dv8tion.jda.core.events.message.MessageReceivedEvent
import net.dv8tion.jda.core.events.role.RoleDeleteEvent
import net.dv8tion.jda.core.exceptions.PermissionException
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import java.util.concurrent.TimeUnit

@Component
class MuteRole
@Autowired constructor(
        private val muteRolesRepository: MuteRolesRepository
) : CommandModule(
        arrayOf("MuteRole"),
        "Name of the mute role or nothing to remove, the role",
        "This command allows you to set the mute role for a guild/server"
) {

    override fun commandExec(event: MessageReceivedEvent, command: String, arguments: String?) {
        if (!event.member.hasPermission(Permission.MANAGE_ROLES)) {
            throw PermissionException("You do not have sufficient permissions to set the mute role for this server")
        }

        val guildId = event.guild.idLong
        if (arguments == null) {
            muteRolesRepository.deleteById(guildId)
            event.channel.sendMessage("Mute role has been removed.").queue { it.delete().queueAfter(1, TimeUnit.MINUTES) }
        } else {
            try {
                muteRolesRepository.save(MuteRole(guildId, event.guild.getRolesByName(arguments, false)[0].idLong))
                event.channel.sendMessage("Role has been set as mute role.").queue { it.delete().queueAfter(1, TimeUnit.MINUTES) }
            } catch (exception: IndexOutOfBoundsException) {
                throw IllegalArgumentException("Couldn't find any roles with that name.", exception)
            }
        }
    }

    override fun onRoleDelete(event: RoleDeleteEvent) {
        muteRolesRepository.deleteByRoleIdAndGuildId(event.role.idLong, event.guild.idLong)
    }

    fun getMuteRole(guild: Guild): Role {
        val roleId = muteRolesRepository.findById(guild.idLong).orElseThrow { throw IllegalStateException("This guild does not have a mute role set up.") }.roleId!!

        return guild.getRoleById(roleId)
    }
}