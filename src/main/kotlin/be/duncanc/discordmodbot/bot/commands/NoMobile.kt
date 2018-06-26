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

import net.dv8tion.jda.core.MessageBuilder
import net.dv8tion.jda.core.entities.ChannelType
import net.dv8tion.jda.core.entities.Guild
import net.dv8tion.jda.core.events.message.MessageReceivedEvent
import org.springframework.stereotype.Component
import java.util.concurrent.TimeUnit

@Component
class NoMobile : CommandModule(
        arrayOf("NoMobile"),
        null,
        "This will assign the \"No mobile verification\" role to all mutual guilds that use this system."
) {

    override fun commandExec(event: MessageReceivedEvent, command: String, arguments: String?) {
        if (!event.isFromType(ChannelType.PRIVATE)) {
            throw UnsupportedOperationException("You are trying to use a command that is intended to be used by direct messaging.\n" +
                    "If you are using this command in a text channel of the guild you are trying to join you are doing something wrong.")
        }

        val guildList = ArrayList<Guild>()
        event.author.mutualGuilds.filter { it.getMember(event.author).roles.isEmpty() && it.getRolesByName("No mobile verification", true).size == 1 }.forEach {
            val member = it.getMember(event.author)
            val role = it.getRolesByName("No mobile verification", true)[0]
            it.controller.addSingleRoleToMember(member, role).reason("Requested with !NoMobile command").queue()
            guildList.add(it)
        }
        if (guildList.isEmpty()) {
            event.channel.sendMessage("There were no guilds/servers were this action could be performed.").queue { it.delete().queueAfter(1, TimeUnit.MINUTES) }
        } else {
            val message = MessageBuilder().append("You now have the ability to chat in the following guilds/servers without mobile verification:")
            guildList.forEach { message.append("\n").append(it.name) }
            event.channel.sendMessage(message.build()).queue { it.delete().queueAfter(1, TimeUnit.MINUTES) }
        }
    }
}