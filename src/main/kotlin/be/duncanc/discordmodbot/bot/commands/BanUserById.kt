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
import be.duncanc.discordmodbot.bot.utils.nicknameAndUsername
import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.MessageBuilder
import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.entities.ChannelType
import net.dv8tion.jda.api.entities.PrivateChannel
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import org.springframework.stereotype.Component
import java.awt.Color
import java.util.*
import java.util.concurrent.TimeUnit


/**
 * Created by Duncan on 22/05/2017.
 * This class creates a command to ban users by id with logging.
 */
/**
 * Constructor for abstract class
 */
@Component
class BanUserById : CommandModule(
    arrayOf("BanByUserId", "BanById"),
    "[user id] [reason~]",
    "Will ban the user with the id, clear all message that where posted by the user the last 24 hours and log it to the log channel.",
    true,
    true
) {

    public override fun commandExec(event: MessageReceivedEvent, command: String, arguments: String?) {
        event.author.openPrivateChannel().queue(
            { privateChannel -> commandExec(event, arguments, privateChannel) }
        ) { commandExec(event, arguments, null as PrivateChannel?) }
    }

    fun commandExec(event: MessageReceivedEvent, arguments: String?, privateChannel: PrivateChannel?) {
        if (!event.isFromType(ChannelType.TEXT)) {
            if (privateChannel != null) {
                event.channel.sendMessage("This command only works in a guild.")
                    .queue { message -> message.delete().queueAfter(1, TimeUnit.MINUTES) }
            }
        } else if (event.member?.hasPermission(Permission.BAN_MEMBERS) != true) {
            event.channel.sendMessage("You need ban members permission to use this command.")
                .queue { message -> message.delete().queueAfter(1, TimeUnit.MINUTES) }
        } else {
            val userId: String
            try {
                userId = arguments!!.split(" ".toRegex()).dropLastWhile { it.isEmpty }.toTypedArray()[0]
            } catch (e: NullPointerException) {
                throw IllegalArgumentException("No id provided")
            }

            val reason: String
            try {
                reason =
                    arguments.substring(arguments.split(" ".toRegex()).dropLastWhile { it.isEmpty }
                        .toTypedArray()[0].length + 1)
            } catch (e: IndexOutOfBoundsException) {
                throw IllegalArgumentException("No reason provided for this action.")
            }

            event.jda.retrieveUserById(userId).queue({ toBan ->
                val toBanMemberCheck = event.guild.getMember(toBan)
                if (toBanMemberCheck != null && event.member?.canInteract(toBanMemberCheck) != true) {
                    privateChannel?.sendMessage("You can't ban a user that you can't interact with.")?.queue()
                    return@queue
                }
                event.guild.ban(userId, 1).queue(banQue@{
                    val guildLogger = event.jda.registeredListeners.firstOrNull { it is GuildLogger } as GuildLogger?
                    if (guildLogger != null) {
                        val logEmbed = EmbedBuilder()
                            .setColor(Color.RED)
                            .setTitle("User banned by id")
                            .addField("UUID", UUID.randomUUID().toString(), false)
                            .addField("User", toBan.name, true)
                            .addField("Moderator", event.member!!.nicknameAndUsername, true)
                            .addField("Reason", reason, false)

                        guildLogger.log(logEmbed, toBan, event.guild, null, GuildLogger.LogTypeAction.MODERATOR)
                    }

                    if (privateChannel == null) {
                        return@banQue
                    }

                    val creatorMessage = MessageBuilder()
                        .append("Banned ").append(toBan.toString())
                        .build()
                    privateChannel.sendMessage(creatorMessage).queue()
                }) banQueThrowable@{ throwable ->
                    if (privateChannel == null) {
                        return@banQueThrowable
                    }

                    val creatorMessage = MessageBuilder()
                        .append("Banning user failed\n")
                        .append(throwable.javaClass.simpleName).append(": ").append(throwable.message)
                        .build()
                    privateChannel.sendMessage(creatorMessage).queue()
                }
            }) { throwable ->
                if (privateChannel == null) {
                    return@queue
                }

                val creatorMessage = MessageBuilder()
                    .append("Failed retrieving the user, banning failed.\n")
                    .append(throwable.javaClass.simpleName).append(": ").append(throwable.message)
                    .build()
                privateChannel.sendMessage(creatorMessage).queue()
            }
        }
    }
}
