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

import be.duncanc.discordmodbot.bot.services.GuildLogger
import be.duncanc.discordmodbot.bot.utils.limitLessBulkDeleteByIds
import be.duncanc.discordmodbot.bot.utils.nicknameAndUsername
import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.entities.channel.ChannelType
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import org.springframework.stereotype.Component
import java.awt.Color
import java.time.OffsetDateTime
import java.util.*
import java.util.concurrent.TimeUnit

/**
 * This command purges a channel of messages.
 */
@Component
class PurgeChannel : CommandModule(
    arrayOf("PurgeChannel", "Purge"),
    "[Amount of messages] (Mention user(s) to filter on)",
    "Cleans the amount of messages given as argument in the channel where executed. If (a) user(s) are/is mentioned at the end of this command only their/his messages will be deleted, but due to limitations in the discord api this **only works on users that are present in the server**, mentioning a user that is not on the server will causes the command to think you mentioned nobody wiping everyone's messages. (Messages older than 2 weeks are ignored due to api issues.)",
    cleanCommandMessage = false,
    ignoreWhitelist = true
) {

    override fun commandExec(event: MessageReceivedEvent, command: String, arguments: String?) {
        try {
            event.message.delete().submit().get(30, TimeUnit.SECONDS)
        } catch (ignored: Exception) {
            // Ignored
        }

        val args = arguments!!.split(" ".toRegex()).dropLastWhile { it.isBlank() }.toTypedArray()
        if (!event.isFromType(ChannelType.TEXT)) {
            event.channel.sendMessage("This command only works in a guild.").queue()
        } else if (event.member?.hasPermission(event.guildChannel.asTextChannel(), Permission.MESSAGE_MANAGE) != true) {
            event.channel.sendMessage(event.author.asMention + " you need manage messages permission in this channel to use this command.")
                .queue { message -> message.delete().queueAfter(1, TimeUnit.MINUTES) }
        } else if (event.message.mentions.users.size > 0) {
            val amount: Int
            try {
                amount = parseAmountOfMessages(args[0])
            } catch (ex: NumberFormatException) {
                event.channel.sendMessage(event.author.asMention + " the first argument needs to be a number of maximum 100 and minimum 2")
                    .queue { message -> message.delete().queueAfter(1, TimeUnit.MINUTES) }
                return
            }

            val textChannel = event.guildChannel.asTextChannel()
            val messageList = ArrayList<Long>()
            val targetUsers = event.message.mentions.users
            for (m in textChannel.iterableHistory.cache(false)) {
                if (targetUsers.contains(m.author) && m.timeCreated.isAfter(OffsetDateTime.now().minusWeeks(2))) {
                    messageList.add(m.idLong)
                } else if (m.timeCreated.isBefore(OffsetDateTime.now().minusWeeks(2))) {
                    break
                }
                if (messageList.size >= amount) {
                    break
                }
            }
            val amountDeleted = messageList.size
            textChannel.limitLessBulkDeleteByIds(messageList)
            val stringBuilder = StringBuilder(event.author.asMention).append(" deleted ").append(amountDeleted)
                .append(" most recent messages from ")
            for (i in targetUsers.indices) {
                stringBuilder.append(targetUsers[i].asMention)
                if (i != targetUsers.size - 1) {
                    stringBuilder.append(", ")
                } else {
                    stringBuilder.append('.')
                }
            }
            event.channel.sendMessage(stringBuilder.toString())
                .queue { message -> message.delete().queueAfter(1, TimeUnit.MINUTES) }


            val logToChannel = event.jda.registeredListeners.firstOrNull { it is GuildLogger } as GuildLogger?
            if (logToChannel != null) {
                val filterString = StringBuilder()
                targetUsers.forEach { user -> filterString.append(user.asMention).append("\n") }
                val logEmbed = EmbedBuilder()
                    .setColor(Color.YELLOW)
                    .setTitle("Filtered channel purge", null)
                    .addField("Moderator", event.member!!.nicknameAndUsername, true)
                    .addField("Channel", textChannel.name, true)
                    .addField("Filter", filterString.toString(), true)

                logToChannel.log(logEmbed, event.author, event.guild, null, GuildLogger.LogTypeAction.MODERATOR)
            }

        } else {
            val amount: Int
            try {
                amount = parseAmountOfMessages(args[0])
            } catch (ex: NumberFormatException) {
                event.channel.sendMessage(event.author.asMention + " the first argument needs to be a number of maximum 1000 and minimum 2")
                    .queue { message -> message.delete().queueAfter(1, TimeUnit.MINUTES) }
                return
            }

            val textChannel = event.guildChannel.asTextChannel()
            val messageList = ArrayList<Long>()
            for (m in textChannel.iterableHistory.cache(false)) {
                if (m.timeCreated.isAfter(OffsetDateTime.now().minusWeeks(2))) {
                    messageList.add(m.idLong)
                } else {
                    break
                }
                if (messageList.size >= amount) {
                    break
                }
            }
            val amountDeleted = messageList.size
            textChannel.limitLessBulkDeleteByIds(messageList)
            textChannel.sendMessage(event.author.asMention + " deleted " + amountDeleted + " most recent messages not older than 2 weeks.")
                .queue { message -> message.delete().queueAfter(1, TimeUnit.MINUTES) }

            val guildLogger = event.jda.registeredListeners.firstOrNull { it is GuildLogger } as GuildLogger?
            if (guildLogger != null) {
                val logEmbed = EmbedBuilder()
                    .setColor(Color.YELLOW)
                    .setTitle("Channel purge", null)
                    .addField("Moderator", event.member!!.nicknameAndUsername, true)
                    .addField("Channel", textChannel.name, true)

                guildLogger.log(logEmbed, event.author, event.guild, null, GuildLogger.LogTypeAction.MODERATOR)
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
