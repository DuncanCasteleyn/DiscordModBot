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
import be.duncanc.discordmodbot.bot.utils.JDALibHelper
import net.dv8tion.jda.core.EmbedBuilder
import net.dv8tion.jda.core.MessageBuilder
import net.dv8tion.jda.core.Permission
import net.dv8tion.jda.core.entities.ChannelType
import net.dv8tion.jda.core.entities.Member
import net.dv8tion.jda.core.entities.Message
import net.dv8tion.jda.core.entities.PrivateChannel
import net.dv8tion.jda.core.events.message.MessageReceivedEvent
import net.dv8tion.jda.core.exceptions.PermissionException
import net.dv8tion.jda.core.requests.RestAction
import org.springframework.stereotype.Component
import java.awt.Color
import java.util.*
import java.util.concurrent.TimeUnit

/**
 * Created by Duncan on 24/02/2017.
 *
 *
 * This class creates a command to ban users with logging.
 */
@Component
class Ban : CommandModule(
        arrayOf("Ban"),
        "[User mention] [Reason~]",
        "Will ban the mentioned user, clear all message that where posted by the user in the last 24 hours and log it to the log channel.",
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
                event.channel.sendMessage("This command only works in a guild.").queue { message -> message.delete().queueAfter(1, TimeUnit.MINUTES) }
            }
        } else if (!event.member.hasPermission(Permission.BAN_MEMBERS)) {
            privateChannel?.sendMessage(event.author.asMention + " you don't have permission to ban!")?.queue { message -> message.delete().queueAfter(1, TimeUnit.MINUTES) }
        } else if (event.message.mentionedUsers.size < 1) {
            privateChannel?.sendMessage("Illegal argumentation, you need to mention a user that is still in the server.")?.queue()
        } else {
            val reason: String
            try {
                reason = arguments!!.substring(arguments.split(" ".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()[0].length + 1)
            } catch (e: IndexOutOfBoundsException) {
                throw IllegalArgumentException("No reason provided for this action.")
            }

            val toBan = event.guild.getMember(event.message.mentionedUsers[0])
            if (!event.member.canInteract(toBan)) {
                throw PermissionException("You can't interact with this member")
            }
            val banRestAction = event.guild.controller.ban(toBan, 1, reason)
            val description = StringBuilder("Reason: $reason")
            if (event.guild.idLong == 175856762677624832L) {
                description.append("\n\n")
                        .append("If you'd like to appeal the ban, please use this form: https://goo.gl/forms/SpWg49gaQlMt4lSG3")
                //todo make this configurable per guild.
            }
            val userBanNotification = EmbedBuilder()
                    .setColor(Color.red)
                    .setAuthor(JDALibHelper.getEffectiveNameAndUsername(event.member), null, event.author.effectiveAvatarUrl)
                    .setTitle(event.guild.name + ": You have been banned by " + JDALibHelper.getEffectiveNameAndUsername(event.member), null)
                    .setDescription(description.toString())
                    .build()

            toBan.user.openPrivateChannel().queue(
                    { privateChannelUserToMute ->
                        privateChannelUserToMute.sendMessage(userBanNotification).queue(
                                { message -> onSuccessfulInformUser(event, reason, privateChannel, toBan, message, banRestAction) }
                        ) { throwable -> onFailToInformUser(event, reason, privateChannel, toBan, throwable, banRestAction) }
                    }
            ) { throwable -> onFailToInformUser(event, reason, privateChannel, toBan, throwable, banRestAction) }
        }
    }

    private fun logBan(event: MessageReceivedEvent, reason: String, toBan: Member) {
        val guildLogger = event.jda.registeredListeners.firstOrNull { it is GuildLogger } as GuildLogger?
        if (guildLogger != null) {
            val logEmbed = EmbedBuilder()
                    .setColor(Color.RED)
                    .setTitle("User banned")
                    .addField("UUID", UUID.randomUUID().toString(), false)
                    .addField("User", JDALibHelper.getEffectiveNameAndUsername(toBan), true)
                    .addField("Moderator", JDALibHelper.getEffectiveNameAndUsername(event.member), true)
                    .addField("Reason", reason, false)


            guildLogger.log(logEmbed, toBan.user, event.guild, null, GuildLogger.LogTypeAction.MODERATOR)
        }
    }

    private fun onSuccessfulInformUser(event: MessageReceivedEvent, reason: String, privateChannel: PrivateChannel?, toBan: Member, userBanWarning: Message, banRestAction: RestAction<Void>) {
        banRestAction.queue({
            logBan(event, reason, toBan)
            if (privateChannel == null) {
                return@queue
            }

            val creatorMessage = MessageBuilder()
                    .append("Banned ").append(toBan.toString()).append(".\n\nThe following message was sent to the user:")
                    .setEmbed(userBanWarning.embeds[0])
                    .build()
            privateChannel.sendMessage(creatorMessage).queue()
        }) { throwable ->
            userBanWarning.delete().queue()
            if (privateChannel == null) {
                return@queue
            }

            val creatorMessage = MessageBuilder()
                    .append("Ban failed on ").append(toBan.toString())
                    .append(throwable.javaClass.simpleName).append(": ").append(throwable.message)
                    .append(".\n\nThe following message was sent to the user but was automatically deleted:")
                    .setEmbed(userBanWarning.embeds[0])
                    .build()
            privateChannel.sendMessage(creatorMessage).queue()
        }
    }

    private fun onFailToInformUser(event: MessageReceivedEvent, reason: String, privateChannel: PrivateChannel?, toBan: Member, throwable: Throwable, banRestAction: RestAction<Void>) {
        banRestAction.queue({
            logBan(event, reason, toBan)
            if (privateChannel == null) {
                return@queue
            }

            val creatorMessage = MessageBuilder()
                    .append("Banned ").append(toBan.toString())
                    .append(".\n\nWas unable to send a DM to the user please inform the user manually, if possible.\n")
                    .append(throwable.javaClass.simpleName).append(": ").append(throwable.message)
                    .build()
            privateChannel.sendMessage(creatorMessage).queue()
        }) { banThrowable ->
            if (privateChannel == null) {
                return@queue
            }

            val creatorMessage = MessageBuilder()
                    .append("Ban failed on ").append(toBan.toString())
                    .append("\n\nWas unable to ban the user\n")
                    .append(banThrowable.javaClass.simpleName).append(": ").append(banThrowable.message)
                    .append(".\n\nWas unable to send a DM to the user.\n")
                    .append(throwable.javaClass.simpleName).append(": ").append(throwable.message)
                    .build()
            privateChannel.sendMessage(creatorMessage).queue()
        }
    }
}
