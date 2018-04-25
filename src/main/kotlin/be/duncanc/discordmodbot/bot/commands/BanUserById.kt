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
import be.duncanc.discordmodbot.bot.utils.JDALibHelper
import net.dv8tion.jda.core.EmbedBuilder
import net.dv8tion.jda.core.MessageBuilder
import net.dv8tion.jda.core.Permission
import net.dv8tion.jda.core.entities.ChannelType
import net.dv8tion.jda.core.entities.PrivateChannel
import net.dv8tion.jda.core.events.message.MessageReceivedEvent
import org.springframework.stereotype.Component
import java.awt.Color
import java.util.concurrent.TimeUnit


/**
 * Created by Duncan on 22/05/2017.
 * This class creates a command to ban users by id with logging.
 */
/**
 * Constructor for abstract class
 */
@Component
class BanUserById private constructor() : CommandModule(arrayOf("BanByUserId", "BanById"), "[user id] [reason~]", "Will ban the user with the id, clear all message that where posted by the user the last 24 hours and log it to the log channel.", true, true) {

    public override fun commandExec(event: MessageReceivedEvent, command: String, arguments: String?) {
        event.author.openPrivateChannel().queue(
                { privateChannel -> commandExec(event, arguments, privateChannel) }
        ) { commandExec(event, arguments, null as PrivateChannel?) }
    }

    fun commandExec(event: MessageReceivedEvent, arguments: String?, privateChannel: PrivateChannel?) {
        if (!event.isFromType(ChannelType.TEXT)) {
            if (privateChannel != null) {
                event.channel.sendMessage("This command only works in a guild.").queue { message -> message.delete().queueAfter(1, TimeUnit.MINUTES) }
            }
        } else if (!event.member.hasPermission(Permission.BAN_MEMBERS)) {
            event.channel.sendMessage("You need ban members permission to use this command.").queue { message -> message.delete().queueAfter(1, TimeUnit.MINUTES) }
        } else {
            val userId: String
            try {
                userId = arguments!!.split(" ".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()[0]
            } catch (e: NullPointerException) {
                throw IllegalArgumentException("No id provided")
            }

            val reason: String
            try {
                reason = arguments.substring(arguments.split(" ".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()[0].length + 1)
            } catch (e: IndexOutOfBoundsException) {
                throw IllegalArgumentException("No reason provided for this action.")
            }

            event.jda.retrieveUserById(userId).queue({ toBan ->
                val toBanMemberCheck = event.guild.getMember(toBan)
                if (toBanMemberCheck != null && !event.member.canInteract(toBanMemberCheck)) {
                    privateChannel?.sendMessage("You can't ban a user that you can't interact with.")?.queue()
                    return@queue
                }
                event.guild.controller.ban(userId, 1, reason).queue(banQue@{
                    val guildLogger = event.jda.registeredListeners.firstOrNull { it is GuildLogger } as GuildLogger?
                    val logToChannel = guildLogger?.logger
                    if (logToChannel != null) {
                        val logEmbed = EmbedBuilder()
                                .setColor(Color.RED)
                                .setTitle("User banned by id  | Case: " + GuildLogger.getCaseNumberSerializable(event.guild.idLong))
                                .addField("User", toBan.name, true)
                                .addField("Moderator", JDALibHelper.getEffectiveNameAndUsername(event.member), true)
                                .addField("Reason", reason, false)

                        logToChannel.log(logEmbed, toBan, event.guild, null, GuildLogger.LogTypeAction.MODERATOR)
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