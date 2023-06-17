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
import be.duncanc.discordmodbot.bot.utils.extractReason
import be.duncanc.discordmodbot.bot.utils.findMemberAndCheckCanInteract
import be.duncanc.discordmodbot.bot.utils.nicknameAndUsername
import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.entities.Member
import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.entities.channel.ChannelType
import net.dv8tion.jda.api.entities.channel.concrete.PrivateChannel
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import net.dv8tion.jda.api.requests.RestAction
import net.dv8tion.jda.api.utils.messages.MessageCreateBuilder
import org.springframework.stereotype.Component
import java.awt.Color
import java.util.*
import java.util.concurrent.TimeUnit

/**
 * This class create a kick command that will be logged.
 */
@Component
class Kick : CommandModule(
    arrayOf("Kick"),
    "[User mention] [Reason~]",
    "This command will kick the mentioned users and log this to the log channel. A reason is required.",
    true,
    true
) {

    override fun commandExec(event: MessageReceivedEvent, command: String, arguments: String?) {
        event.author.openPrivateChannel().queue(
            { privateChannel -> commandExec(event, arguments, privateChannel) }
        ) { commandExec(event, arguments, null) }
    }

    private fun commandExec(event: MessageReceivedEvent, arguments: String?, privateChannel: PrivateChannel?) {

        if (!event.isFromType(ChannelType.TEXT)) {
            privateChannel?.sendMessage("This command only works in a guild.")?.queue()
        } else if (event.member?.hasPermission(Permission.KICK_MEMBERS) != true) {
            privateChannel?.sendMessage(event.author.asMention + " you need kick members permission to use this command!")
                ?.queue { message -> message.delete().queueAfter(1, TimeUnit.MINUTES) }
        } else if (event.message.mentions.users.size < 1) {
            privateChannel?.sendMessage("Illegal argumentation, you need to mention a user that is still in the server.")
                ?.queue()
        } else {
            val reason: String = extractReason(arguments)

            val toKick = findMemberAndCheckCanInteract(event)
            val kickRestAction = event.guild.kick(toKick)

            val userKickNotification = EmbedBuilder()
                .setColor(Color.RED)
                .setAuthor(event.member?.nicknameAndUsername, null, event.author.effectiveAvatarUrl)
                .setTitle("${event.guild.name}: You have been kicked by ${event.member?.nicknameAndUsername}", null)
                .setDescription("Reason: $reason")
                .build()

            toKick.user.openPrivateChannel().queue(
                { privateChannelUserToMute ->
                    privateChannelUserToMute.sendMessageEmbeds(userKickNotification).queue(
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
                .addField("Moderator", event.member!!.nicknameAndUsername, true)
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

            val creatorMessage = MessageCreateBuilder()
                .addContent("Kicked ").addContent(toKick.toString())
                .addContent(".\n\nThe following message was sent to the user:")
                .setEmbeds(userKickWarning.embeds)
                .build()
            privateChannel.sendMessage(creatorMessage).queue()
        }) { throwable ->
            userKickWarning.delete().queue()
            if (privateChannel == null) {
                return@queue
            }

            val creatorMessage = MessageCreateBuilder()
                .addContent("Kick failed ").addContent(toKick.toString())
                .addContent(throwable.javaClass.simpleName).addContent(": ").addContent(throwable.message ?: "")
                .addContent(".\n\nThe following message was sent to the user but was automatically deleted:")
                .setEmbeds(userKickWarning.embeds)
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

            val creatorMessage = MessageCreateBuilder()
                .addContent("Kicked ").addContent(toKick.toString())
                .addContent(".\n\nWas unable to send a DM to the user please inform the user manually, if possible.\n")
                .addContent(throwable.javaClass.simpleName).addContent(": ").addContent(throwable.message ?: "")
                .build()
            privateChannel.sendMessage(creatorMessage).queue()
        }) { banThrowable ->
            if (privateChannel == null) {
                return@queue
            }

            val creatorMessage = MessageCreateBuilder()
                .addContent("Kick failed ").addContent(toKick.toString())
                .addContent("\n\nWas unable to ban the user\n")
                .addContent(banThrowable.javaClass.simpleName).addContent(": ").addContent(banThrowable.message ?: "")
                .addContent(".\n\nWas unable to send a DM to the user.\n")
                .addContent(throwable.javaClass.simpleName).addContent(": ").addContent(throwable.message ?: "")
                .build()
            privateChannel.sendMessage(creatorMessage).queue()
        }
    }
}
