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

package be.duncanc.discordmodbot.bot.commands

import be.duncanc.discordmodbot.bot.services.GuildLogger
import be.duncanc.discordmodbot.bot.services.LogToChannel
import be.duncanc.discordmodbot.bot.utils.JDALibHelper
import net.dv8tion.jda.core.EmbedBuilder
import net.dv8tion.jda.core.Permission
import net.dv8tion.jda.core.entities.ChannelType
import net.dv8tion.jda.core.entities.Message
import net.dv8tion.jda.core.events.message.MessageReceivedEvent
import java.awt.Color
import java.time.OffsetDateTime
import java.util.*
import java.util.concurrent.TimeUnit

/**
 * Created by Duncan on 27/02/2017.
 *
 *
 * This command purges a channel of messages.
 */
object PurgeChannel : CommandModule(arrayOf("PurgeChannel", "Purge"), "[Amount of messages] (Mention user(s) to filter on)", "Cleans the amount of messages given as argument in the channel where executed. If (a) user(s) are/is mentioned at the end of this command only their/his messages will be deleted, but due to limitations in the discord api this **only works on users that are present in the server**, mentioning a user that is not on the server will causes the command to think you mentioned nobody wiping everyone's messages. (Messages older than 2 weeks are ignored due to api issues.)", false) {

    public override fun commandExec(event: MessageReceivedEvent, command: String, arguments: String?) {
        try {
            event.message.delete().complete()
        } catch (ignored: Exception) {
        }

        val args = arguments!!.split(" ".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
        if (!event.isFromType(ChannelType.TEXT)) {
            event.channel.sendMessage("This command only works in a guild.").queue()
        } else if (!event.member.hasPermission(event.textChannel, Permission.MESSAGE_MANAGE)) {
            event.channel.sendMessage(event.author.asMention + " you need manage messages permission in this channel to use this command.").queue { message -> message.delete().queueAfter(1, TimeUnit.MINUTES) }
        } else if (event.message.mentionedUsers.size > 0) {
            val amount: Int
            try {
                amount = parseAmountOfMessages(args[0])
            } catch (ex: NumberFormatException) {
                event.channel.sendMessage(event.author.asMention + " the first argument needs to be a number of maximum 100 and minimum 2").queue { message -> message.delete().queueAfter(1, TimeUnit.MINUTES) }
                return
            }

            val textChannel = event.textChannel
            val messageList = ArrayList<Message>()
            val targetUsers = event.message.mentionedUsers
            for (m in textChannel.iterableHistory.cache(false)) {
                if (targetUsers.contains(m.author) && m.creationTime.isAfter(OffsetDateTime.now().minusWeeks(2))) {
                    messageList.add(m)
                } else if (m.creationTime.isBefore(OffsetDateTime.now().minusWeeks(2))) {
                    break
                }
                if (messageList.size >= amount) {
                    break
                }
            }
            val amountDeleted = messageList.size
            JDALibHelper.limitLessBulkDelete(textChannel, messageList)
            val stringBuilder = StringBuilder(event.author.asMention).append(" deleted ").append(amountDeleted).append(" most recent messages from ")
            for (i in targetUsers.indices) {
                stringBuilder.append(targetUsers[i].asMention)
                if (i != targetUsers.size - 1) {
                    stringBuilder.append(", ")
                } else {
                    stringBuilder.append('.')
                }
            }
            event.channel.sendMessage(stringBuilder.toString()).queue { message -> message.delete().queueAfter(1, TimeUnit.MINUTES) }


            val logToChannel = event.jda.registeredListeners.firstOrNull { it is LogToChannel } as LogToChannel?
            if (logToChannel != null) {
                val filterString = StringBuilder()
                targetUsers.forEach { user -> filterString.append(user.asMention).append("\n") }
                val logEmbed = EmbedBuilder()
                        .setColor(Color.YELLOW)
                        .setTitle("Filtered channel purge", null)
                        .addField("Moderator", JDALibHelper.getEffectiveNameAndUsername(event.member), true)
                        .addField("Channel", textChannel.name, true)
                        .addField("Filter", filterString.toString(), true)

                logToChannel.log(logEmbed, event.author, event.guild, null, GuildLogger.LogTypeAction.MODERATOR)
            }

        } else {
            val amount: Int
            try {
                amount = parseAmountOfMessages(args[0])
            } catch (ex: NumberFormatException) {
                event.channel.sendMessage(event.author.asMention + " the first argument needs to be a number of maximum 1000 and minimum 2").queue { message -> message.delete().queueAfter(1, TimeUnit.MINUTES) }
                return
            }

            val textChannel = event.textChannel
            val messageList = ArrayList<Message>()
            for (m in textChannel.iterableHistory.cache(false)) {
                if (m.creationTime.isAfter(OffsetDateTime.now().minusWeeks(2))) {
                    messageList.add(m)
                } else {
                    break
                }
                if (messageList.size >= amount) {
                    break
                }
            }
            val amountDeleted = messageList.size
            JDALibHelper.limitLessBulkDelete(textChannel, messageList)
            textChannel.sendMessage(event.author.asMention + " deleted " + amountDeleted + " most recent messages not older than 2 weeks.").queue { message -> message.delete().queueAfter(1, TimeUnit.MINUTES) }

            val logToChannel = event.jda.registeredListeners.firstOrNull { it is LogToChannel } as LogToChannel?
            if (logToChannel != null) {
                val logEmbed = EmbedBuilder()
                        .setColor(Color.YELLOW)
                        .setTitle("Channel purge", null)
                        .addField("Moderator", JDALibHelper.getEffectiveNameAndUsername(event.member), true)
                        .addField("Channel", textChannel.name, true)

                logToChannel.log(logEmbed, event.author, event.guild, null, GuildLogger.LogTypeAction.MODERATOR)
            }
        }
    }

    @Throws(NumberFormatException::class)
    private fun parseAmountOfMessages(number: String): Int {
        val amount = Integer.parseInt(number)
        if (amount > 1000) {
            throw NumberFormatException("Expected number between 1 and 1000 got $amount.")
        } else if (amount < 1) {
            throw NumberFormatException("Expected number between 1 and 1000 got $amount.")
        }
        return amount
    }
}
