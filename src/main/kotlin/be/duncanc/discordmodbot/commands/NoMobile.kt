/*
 * MIT License
 *
 * Copyright (c) 2017 Duncan Casteleyn
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package be.duncanc.discordmodbot.commands

import net.dv8tion.jda.core.MessageBuilder
import net.dv8tion.jda.core.entities.Guild
import net.dv8tion.jda.core.events.message.MessageReceivedEvent

class NoMobile : CommandModule(ALIASES, null, DESCRIPTION) {

    companion object {
        private val ALIASES = arrayOf("NoMobile")
        private const val DESCRIPTION = "This will asign the \"No mobile verification\" role to all mutual guilds that use this system."
    }

    /**
     * When the command is triggered this function will be called
     *
     * @param event The {@code MessageReceivedEvent}
     * @param command The command that was used.
     * @param arguments The arguments that where provided with the command.
     */
    override fun commandExec(event: MessageReceivedEvent, command: String, arguments: String?) {
        val guildList = ArrayList<Guild>()
        event.author.mutualGuilds.filter { it.getMember(event.author).roles.isEmpty() && it.getRolesByName("No mobile verification", true).size == 1 }.forEach {
            val member = it.getMember(event.author)
            val role = it.getRolesByName("No mobile verification", true)[0]
            it.controller.addSingleRoleToMember(member, role).reason("Requested with !NoMobile command").queue()
            guildList.add(it)
        }
        if(guildList.isEmpty()) {
            event.channel.sendMessage("There were no guilds/servers were this action could be performed.").queue()
        } else {
            val message = MessageBuilder().append("You now have the ability to chat in the following guilds/servers without mobile verification:")
            guildList.forEach { message.append("\n").append(it.name) }
            event.channel.sendMessage(message.build()).queue()
        }
    }
}