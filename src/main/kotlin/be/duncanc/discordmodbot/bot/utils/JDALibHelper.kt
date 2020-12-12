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

import net.dv8tion.jda.api.entities.Member
import net.dv8tion.jda.api.entities.TextChannel
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import net.dv8tion.jda.api.exceptions.PermissionException
import java.time.format.DateTimeFormatter
import java.util.*

/**
 * When a time format has to be displayed generally this format will be used.
 */
val messageTimeFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("dd-MMM-yyyy HH:mm O", Locale.ROOT)

/**
 * Retrieves a string that contains both the nickname and username of a member.
 */
val Member.nicknameAndUsername: String
    get() = if (this.nickname != null) {
        "${this.nickname}(${this.user.name})"
    } else {
        this.user.name
    }

/**
 * Applies the rot13 function on the String
 */
fun String.rot13(): String {
    val sb = StringBuilder()
    for (i in 0 until this.length) {
        var c = this[i]
        when (c) {
            in 'a'..'m' -> c += 13
            in 'A'..'M' -> c += 13
            in 'n'..'z' -> c -= 13
            in 'N'..'Z' -> c -= 13
        }
        sb.append(c)
    }
    return sb.toString()
}

/**
 * Deletes multiple messages at once, unlike the default method this one will split the ArrayList messages in stacks of 100 messages each automatically
 *
 * @param messageIdStrings Messages to delete. The list you provide will be emptied for you.
 */
fun TextChannel.limitLessBulkDeleteByIds(messagesIds: ArrayList<Long>) {
    val messagesIdStrings = ArrayList(messagesIds.map { it.toString() })
    messagesIds.clear()
    if (messagesIdStrings.size in 2..100) {
        this.deleteMessagesByIds(messagesIdStrings).queue()
    } else if (messagesIdStrings.size < 2) {
        for (message in messagesIdStrings) {
            this.deleteMessageById(message).queue()
        }
    } else {
        var messagesStack = ArrayList<String>()
        while (messagesIdStrings.size > 0) {
            messagesStack.add(messagesIdStrings.removeAt(0))
            if (messagesStack.size == 100) {
                this.deleteMessagesByIds(messagesStack).queue()
                messagesStack = ArrayList()
            }
        }
        if (messagesStack.size >= 2) {
            this.deleteMessagesByIds(messagesStack).queue()
        } else {
            for (message in messagesStack) {
                this.deleteMessageById(message).queue()
            }
        }
    }
}

fun extractReason(arguments: String?): String {
    val reason: String
    try {
        reason = extractSentenceIgnoringFirstWord(arguments)
    } catch (e: IndexOutOfBoundsException) {
        throw IllegalArgumentException("No reason provided for this action.")
    }
    return reason
}

private fun extractSentenceIgnoringFirstWord(arguments: String?) =
    arguments!!.substring(getIndexStartOfSecondWord(arguments))

private fun getIndexStartOfSecondWord(arguments: String) =
    arguments.split(" ").dropLastWhile { it.isBlank() }.toTypedArray()[0].length + 1

fun findMemberAndCheckCanInteract(event: MessageReceivedEvent): Member {
    val target = event.guild.getMember(event.message.mentionedUsers[0])!!
    if (event.member?.canInteract(target) != true) {
        throw PermissionException("You can't interact with this member")
    }
    return target
}
