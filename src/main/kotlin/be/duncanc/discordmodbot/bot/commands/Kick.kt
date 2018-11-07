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
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.ApplicationContext
import org.springframework.stereotype.Component
import java.awt.Color
import java.util.*
import java.util.concurrent.TimeUnit

/**
 * This class create a kick command that will be logged.
 */
@Component
class Kick
@Autowired constructor(
    private val applicationContext: ApplicationContext
) : CommandModule(
    arrayOf("Kick"),
    "[User mention] [Reason~]",
    "This command will kick the mentioned users and log this to the log channel. A reason is required.",
    true,
    true
) {

    public override fun commandExec(event: MessageReceivedEvent, command: String, arguments: String?) {
        event.author.openPrivateChannel().queue(
            { privateChannel -> commandExec(event, arguments, privateChannel) }
        ) { commandExec(event, arguments, null) }
    }

    private fun commandExec(event: MessageReceivedEvent, arguments: String?, privateChannel: PrivateChannel?) {

        if (!event.isFromType(ChannelType.TEXT)) {
            privateChannel?.sendMessage("This command only works in a guild.")?.queue()
        } else if (!event.member.hasPermission(Permission.KICK_MEMBERS)) {
            privateChannel?.sendMessage(event.author.asMention + " you need kick members permission to use this command!")
                ?.queue { message -> message.delete().queueAfter(1, TimeUnit.MINUTES) }
        } else if (event.message.mentionedUsers.size < 1) {
            privateChannel?.sendMessage("Illegal argumentation, you need to mention a user that is still in the server.")
                ?.queue()
        } else {
            val reason: String
            try {
                reason =
                        arguments!!.substring(arguments.split(" ".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()[0].length + 1)
            } catch (e: IndexOutOfBoundsException) {
                throw IllegalArgumentException("No reason provided for this action.")
            }

            val toKick = event.guild.getMember(event.message.mentionedUsers[0])
            if (!event.member.canInteract(toKick)) {
                throw PermissionException("You can't interact with this member")
            }
            val kickRestAction = event.guild.controller.kick(toKick, reason)

            val userKickNotification = EmbedBuilder()
                .setColor(Color.RED)
                .setAuthor(event.member.nicknameAndUsername, null, event.author.effectiveAvatarUrl)
                .setTitle("${event.guild.name}: You have been kicked by ${event.member.nicknameAndUsername}", null)
                .setDescription("Reason: $reason")
                .build()

            toKick.user.openPrivateChannel().queue(
                { privateChannelUserToMute ->
                    privateChannelUserToMute.sendMessage(userKickNotification).queue(
                        { message: Message ->
                            onSuccessfulInformUser(event, reason, privateChannel, toKick, message, kickRestAction)
                        }
                    ) { throwable ->
                        onFailToInformUser(
                            event,
                            reason,
                            privateChannel,
                            toKick,
                            throwable,
                            kickRestAction
                        )
                    }
                }
            ) { throwable -> onFailToInformUser(event, reason, privateChannel, toKick, throwable, kickRestAction) }
        }
    }

    private fun logKick(event: MessageReceivedEvent, reason: String, toKick: Member) {
        val guildLogger = event.jda.registeredListeners.firstOrNull { it is GuildLogger } as GuildLogger?
        if (guildLogger != null) {
            val logEmbed = EmbedBuilder()
                .setColor(Color.RED)
                .setTitle("User kicked")
                .addField("UUID", UUID.randomUUID().toString(), false)
                .addField("User", toKick.nicknameAndUsername, true)
                .addField("Moderator", event.member.nicknameAndUsername, true)
                .addField("Reason", reason, false)

            guildLogger.log(logEmbed, toKick.user, event.guild, null, GuildLogger.LogTypeAction.MODERATOR)
        }
    }

    private fun onSuccessfulInformUser(
        event: MessageReceivedEvent,
        reason: String,
        privateChannel: PrivateChannel?,
        toKick: Member,
        userKickWarning: Message,
        kickRestAction: RestAction<Void>
    ) {
        kickRestAction.queue({
            logKick(event, reason, toKick)
            if (privateChannel == null) {
                return@queue
            }

            val creatorMessage = MessageBuilder()
                .append("Kicked ").append(toKick.toString()).append(".\n\nThe following message was sent to the user:")
                .setEmbed(userKickWarning.embeds[0])
                .build()
            privateChannel.sendMessage(creatorMessage).queue()
        }) { throwable ->
            userKickWarning.delete().queue()
            if (privateChannel == null) {
                return@queue
            }

            val creatorMessage = MessageBuilder()
                .append("Kick failed ").append(toKick.toString())
                .append(throwable.javaClass.simpleName).append(": ").append(throwable.message)
                .append(".\n\nThe following message was sent to the user but was automatically deleted:")
                .setEmbed(userKickWarning.embeds[0])
                .build()
            privateChannel.sendMessage(creatorMessage).queue()
        }
    }

    private fun onFailToInformUser(
        event: MessageReceivedEvent,
        reason: String,
        privateChannel: PrivateChannel?,
        toKick: Member,
        throwable: Throwable,
        kickRestAction: RestAction<Void>
    ) {
        kickRestAction.queue({
            logKick(event, reason, toKick)
            if (privateChannel == null) {
                return@queue
            }

            val creatorMessage = MessageBuilder()
                .append("Kicked ").append(toKick.toString())
                .append(".\n\nWas unable to send a DM to the user please inform the user manually, if possible.\n")
                .append(throwable.javaClass.simpleName).append(": ").append(throwable.message)
                .build()
            privateChannel.sendMessage(creatorMessage).queue()
        }) { banThrowable ->
            if (privateChannel == null) {
                return@queue
            }

            val creatorMessage = MessageBuilder()
                .append("Kick failed ").append(toKick.toString())
                .append("\n\nWas unable to ban the user\n")
                .append(banThrowable.javaClass.simpleName).append(": ").append(banThrowable.message)
                .append(".\n\nWas unable to send a DM to the user.\n")
                .append(throwable.javaClass.simpleName).append(": ").append(throwable.message)
                .build()
            privateChannel.sendMessage(creatorMessage).queue()
        }
    }
}
