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
 */

package be.duncanc.discordmodbot.bot.commands

import be.duncanc.discordmodbot.bot.services.GuildLogger
import be.duncanc.discordmodbot.bot.services.ModNotes
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
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.ApplicationContext
import org.springframework.stereotype.Component
import java.awt.Color
import java.util.concurrent.TimeUnit

/**
 * This class create a kick command that will be logged.
 */
@Component
class Kick private constructor() : CommandModule(arrayOf("Kick"), "[User mention] [Reason~]", "This command will kick the mentioned users and log this to the log channel. A reason is required.", true, true) {

    @Autowired
    private lateinit var applicationContext: ApplicationContext

    public override fun commandExec(event: MessageReceivedEvent, command: String, arguments: String?) {
        event.author.openPrivateChannel().queue(
                { privateChannel -> commandExec(event, arguments, privateChannel) }
        ) { commandExec(event, arguments, null) }
    }

    private fun commandExec(event: MessageReceivedEvent, arguments: String?, privateChannel: PrivateChannel?) {

        if (!event.isFromType(ChannelType.TEXT)) {
            privateChannel?.sendMessage("This command only works in a guild.")?.queue()
        } else if (!event.member.hasPermission(Permission.KICK_MEMBERS)) {
            privateChannel?.sendMessage(event.author.asMention + " you need kick members permission to use this command!")?.queue { message -> message.delete().queueAfter(1, TimeUnit.MINUTES) }
        } else if (event.message.mentionedUsers.size < 1) {
            privateChannel?.sendMessage("Illegal argumentation, you need to mention a user that is still in the server.")?.queue()
        } else {
            val reason: String
            try {
                reason = arguments!!.substring(arguments.split(" ".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()[0].length + 1)
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
                    .setAuthor(JDALibHelper.getEffectiveNameAndUsername(event.member), null, event.author.effectiveAvatarUrl)
                    .setTitle(event.guild.name + ": You have been kicked by " + JDALibHelper.getEffectiveNameAndUsername(event.member), null)
                    .setDescription("Reason: $reason")
                    .build()

            toKick.user.openPrivateChannel().queue(
                    { privateChannelUserToMute ->
                        privateChannelUserToMute.sendMessage(userKickNotification).queue(
                                { message: Message ->
                                    onSuccessfulInformUser(event, reason, privateChannel, toKick, message, kickRestAction)
                                    applicationContext.getBean(ModNotes::class.java).addNote(reason, ModNotes.NoteType.WARN, toKick.user.idLong, event.guild.idLong, event.author.idLong)
                                }
                        ) { throwable -> onFailToInformUser(event, reason, privateChannel, toKick, throwable, kickRestAction) }
                    }
            ) { throwable -> onFailToInformUser(event, reason, privateChannel, toKick, throwable, kickRestAction) }
        }
    }

    private fun logKick(event: MessageReceivedEvent, reason: String, toKick: Member) {
        val guildLogger = event.jda.registeredListeners.firstOrNull { it is GuildLogger } as GuildLogger?
        val logToChannel = guildLogger?.logger
        if (logToChannel != null) {
            val logEmbed = EmbedBuilder()
                    .setColor(Color.RED)
                    .setTitle("User kicked | Case: " + GuildLogger.getCaseNumberSerializable(event.guild.idLong))
                    .addField("User", JDALibHelper.getEffectiveNameAndUsername(toKick), true)
                    .addField("Moderator", JDALibHelper.getEffectiveNameAndUsername(event.member), true)
                    .addField("Reason", reason, false)

            logToChannel.log(logEmbed, toKick.user, event.guild, null, GuildLogger.LogTypeAction.MODERATOR)
        }
    }

    private fun onSuccessfulInformUser(event: MessageReceivedEvent, reason: String, privateChannel: PrivateChannel?, toKick: Member, userKickWarning: Message, kickRestAction: RestAction<Void>) {
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

    private fun onFailToInformUser(event: MessageReceivedEvent, reason: String, privateChannel: PrivateChannel?, toKick: Member, throwable: Throwable, kickRestAction: RestAction<Void>) {
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
