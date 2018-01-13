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
 *
 */

package be.duncanc.discordmodbot.utils

import net.dv8tion.jda.core.entities.Member
import net.dv8tion.jda.core.entities.Message
import net.dv8tion.jda.core.entities.TextChannel
import java.util.*

/**
 * This object contains methods that can be used to improve the usage of JDA methods
 */
object JDALibHelper {

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
        if (member.nickname != null) {
            return member.nickname + "(" + member.user.name + ")"
        } else {
            return member.user.name
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
                    messagesStack = ArrayList<Message>()
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
