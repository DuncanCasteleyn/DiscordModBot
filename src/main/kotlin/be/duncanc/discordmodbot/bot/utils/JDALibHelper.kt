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

package be.duncanc.discordmodbot.bot.utils

import net.dv8tion.jda.core.entities.Member
import net.dv8tion.jda.core.entities.Message
import net.dv8tion.jda.core.entities.TextChannel
import java.time.format.DateTimeFormatter
import java.util.*

/**
 * This object contains methods that can be used to improve the usage of JDA methods
 */
object JDALibHelper {

    /**
     * When a time format has to be displayed generally this format will be used.
     */
    val messageTimeFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("dd-MMM-yyyy HH:mm O")

    /**
     * Create a string that contains both the username and nickname of a member.
     *
     * @param member the member object to creat the string from.
     * @return A string containing both the nickname and username in form of nickname(username).
     */
    fun getEffectiveNameAndUsername(member: Member?): String {
        if (member == null) {
            throw IllegalArgumentException("Member may not be null")
        }
        return if (member.nickname != null) {
            member.nickname + "(" + member.user.name + ")"
        } else {
            member.user.name
        }
    }

    /**
     * Deletes multiple messages at once, unlike the default method this one will split the ArrayList messages in stacks of 100 messages each automatically
     *
     * @param channel  channel to deletes the messages from.
     * @param messages Messages to deleted. The list you give will be emptied for you.
     */
    fun limitLessBulkDelete(channel: TextChannel, messages: ArrayList<Message>) {
        if (messages.size in 2..100) {
            channel.deleteMessages(messages).queue()
        } else if (messages.size < 2) {
            for (message in messages) {
                message.delete().queue()
            }
        } else {
            var messagesStack = ArrayList<Message>()
            while (messages.size > 0) {
                messagesStack.add(messages.removeAt(0))
                if (messagesStack.size == 100) {
                    channel.deleteMessages(messagesStack).queue()
                    messagesStack = ArrayList()
                }
            }
            if (messagesStack.size >= 2) {
                channel.deleteMessages(messagesStack).queue()
            } else {
                for (message in messagesStack) {
                    message.delete().queue()
                }
            }
        }
        messages.clear()
    }
}
