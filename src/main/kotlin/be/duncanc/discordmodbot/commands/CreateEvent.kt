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

import be.duncanc.discordmodbot.sequences.Sequence
import net.dv8tion.jda.core.Permission
import net.dv8tion.jda.core.entities.*
import net.dv8tion.jda.core.events.message.MessageReceivedEvent

object CreateEvent : CommandModule(arrayOf("CreateEvent"), "<event id/name> <subscribers role> <emote to react to> <event text>", "Creates an event, including role and message to announce the event", requiredPermissions = *arrayOf(Permission.MANAGE_ROLES)) {
    override fun commandExec(event: MessageReceivedEvent, command: String, arguments: String?) {
        event.jda.addEventListener(EventCreationSequence(event.author, event.textChannel))
    }

    class EventCreationSequence(user: User, channel: MessageChannel) : Sequence(user, channel, cleanAfterSequence = true, informUser = true) {
        private var eventName :String? = null
        private var eventRole :Role? = null
        private var reactEmote :Emote? = null
        private var announceChannel:TextChannel? = null

        init {
            channel.sendMessage("Please enter the event id/name").queue()
        }

        override fun onMessageReceivedDuringSequence(event: MessageReceivedEvent) {
            when {
                eventName == null -> {
                    eventName = event.message.contentStripped
                    channel.sendMessage("Please mention the role you wanted to be used.").queue()
                }
                eventRole == null -> {
                    eventRole = event.message.mentionedRoles[0]
                    channel.sendMessage("Please post the emote to be used.").queue()
                }
                reactEmote == null -> {
                    reactEmote = event.message.emotes[0]
                    channel.sendMessage("Please mention the channel were you want the announcement to be made.").queue()
                }
                announceChannel == null -> {
                    announceChannel = event.message.mentionedChannels[0]
                    channel.sendMessage("Please enter the announcement text.").queue()
                }
                else -> TODO()
            }
        }
    }
}