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

package be.duncanc.discordmodbot.bot.commands

import be.duncanc.discordmodbot.data.services.UserBlockService
import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.entities.Role
import net.dv8tion.jda.api.entities.channel.ChannelType
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import net.dv8tion.jda.api.utils.SplitUtil
import org.springframework.stereotype.Component
import java.util.concurrent.TimeUnit

/**
 * Commands to get all role ids of the current guild where executed.
 */
@Component
class RoleIds(
    userBlockService: UserBlockService
) : CommandModule(
    ALIASES,
    null,
    DESCRIPTION,
    userBlockService = userBlockService
) {
    companion object {
        private val ALIASES = arrayOf("RoleIds", "GetRoleIds")
        private const val DESCRIPTION = "Get all the role ids of the guild where executed."
    }

    override fun commandExec(event: MessageReceivedEvent, command: String, arguments: String?) {
        if (!event.isFromType(ChannelType.TEXT)) {
            event.channel.sendMessage("This command only works in a guild.")
                .queue { message -> message.delete().queueAfter(1, TimeUnit.MINUTES) }
        } else if (event.member?.hasPermission(Permission.MANAGE_ROLES) != true) {
            event.channel.sendMessage(event.author.asMention + " you need manage roles permission to use this command.")
                .queue { message -> message.delete().queueAfter(1, TimeUnit.MINUTES) }
        } else {
            val result = StringBuilder()
            event.guild.roles.forEach { role: Role -> result.append(role.toString()).append("\n") }
            event.author.openPrivateChannel().queue { privateChannel ->
                val messages = SplitUtil.split(
                    result.toString(),
                    Message.MAX_CONTENT_LENGTH - 10,
                    SplitUtil.Strategy.NEWLINE
                )
                messages.forEach { message -> privateChannel.sendMessage(message).queue() }
            }
        }
    }
}
