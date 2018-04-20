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

package be.duncanc.discordmodbot.commands

import be.duncanc.discordmodbot.services.GuildLogger
import be.duncanc.discordmodbot.services.LogToChannel
import be.duncanc.discordmodbot.services.ModNotes
import be.duncanc.discordmodbot.utils.JDALibHelper
import net.dv8tion.jda.core.EmbedBuilder
import net.dv8tion.jda.core.MessageBuilder
import net.dv8tion.jda.core.Permission
import net.dv8tion.jda.core.entities.ChannelType
import net.dv8tion.jda.core.entities.Member
import net.dv8tion.jda.core.entities.MessageEmbed
import net.dv8tion.jda.core.entities.PrivateChannel
import net.dv8tion.jda.core.events.message.MessageReceivedEvent
import net.dv8tion.jda.core.exceptions.PermissionException
import java.awt.Color

/**
 * Created by Duncan on 24/02/2017.
 *
 *
 * This class creates a command that allowed you to warn users by sending them a dm and logging.
 */
object Warn : CommandModule(arrayOf("Warn"), "[User mention] [Reason~]", "Warns as user by sending the user mentioned a message and logs the warning to the log channel.", true, true) {

    public override fun commandExec(event: MessageReceivedEvent, command: String, arguments: String?) {
        event.author.openPrivateChannel().queue(
                { privateChannel -> commandExec(event, arguments, privateChannel) }
        ) { commandExec(event, arguments, null as PrivateChannel?) }
    }

    private fun commandExec(event: MessageReceivedEvent, arguments: String?, privateChannel: PrivateChannel?) {
        if (!event.isFromType(ChannelType.TEXT)) {
            privateChannel?.sendMessage("This command only works in a guild.")?.queue()
        } else if (!(event.member.hasPermission(Permission.KICK_MEMBERS) || event.member.hasPermission(Permission.BAN_MEMBERS))) {
            privateChannel?.sendMessage(event.author.asMention + " you need kick/ban members permissions to warn users.")?.queue()
        } else if (event.message.mentionedUsers.size < 1) {
            privateChannel?.sendMessage("Illegal argumentation, you need to mention a user that is still in the server.")?.queue()
        } else {
            val reason: String
            try {
                reason = arguments!!.substring(arguments.split(" ".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()[0].length + 1)
            } catch (e: IndexOutOfBoundsException) {
                throw IllegalArgumentException("No reason provided for this action.")
            }

            val toWarn = event.guild.getMember(event.message.mentionedUsers[0])
            if (!event.member.canInteract(toWarn)) {
                throw PermissionException("You can't interact with this member")
            }
            val logToChannel = event.jda.registeredListeners.firstOrNull { it is LogToChannel } as LogToChannel?
            if (logToChannel != null) {
                val serializableCaseResult = GuildLogger.getCaseNumberSerializable(event.guild.idLong)
                val logEmbed = EmbedBuilder()
                        .setColor(Color.YELLOW)
                        .setTitle("User warned | Case: $serializableCaseResult")
                        .addField("User", JDALibHelper.getEffectiveNameAndUsername(toWarn), true)
                        .addField("Moderator", JDALibHelper.getEffectiveNameAndUsername(event.member), true)
                        .addField("Reason", reason, false)

                logToChannel.log(logEmbed, toWarn.user, event.guild, null, GuildLogger.LogTypeAction.MODERATOR)

                //runBots.getLogToChannel().log(JDALibHelper.getEffectiveNameAndUsername(event.getMember()) + " warned " + JDALibHelper.getEffectiveNameAndUsername(toWarn), "Reason: " + arguments, event.getGuild(), toWarn.getUser().getId(), toWarn.getUser().getEffectiveAvatarUrl(), true);
            }

            val userWarning = EmbedBuilder()
                    .setColor(Color.YELLOW)
                    .setAuthor(JDALibHelper.getEffectiveNameAndUsername(event.member), null, event.author.effectiveAvatarUrl)
                    .setTitle(event.guild.name + ": You have been warned by " + JDALibHelper.getEffectiveNameAndUsername(event.member), null)
                    .addField("Reason", reason, false)

            toWarn.user.openPrivateChannel().queue(
                    { privateChannelUserToWarn ->
                        privateChannelUserToWarn.sendMessage(userWarning.build()).queue(
                                { onSuccessfulWarnUser(privateChannel!!, toWarn, userWarning.build()) }
                        ) { throwable -> onFailToWarnUser(privateChannel!!, toWarn, throwable) }
                    }
            ) { throwable -> onFailToWarnUser(privateChannel!!, toWarn, throwable) }

            ModNotes.addNote(reason, ModNotes.NoteType.WARN, toWarn.user.idLong, event.guild.idLong, event.author.idLong)
        }
    }

    private fun onSuccessfulWarnUser(privateChannel: PrivateChannel, toWarn: Member, userWarning: MessageEmbed) {
        val creatorMessage = MessageBuilder()
                .append("Warned ").append(toWarn.toString()).append(".\n\nThe following message was sent to the user:")
                .setEmbed(userWarning)
                .build()
        privateChannel.sendMessage(creatorMessage).queue()
    }

    private fun onFailToWarnUser(privateChannel: PrivateChannel, toWarn: Member, throwable: Throwable) {
        val creatorMessage = MessageBuilder()
                .append("Warned ").append(toWarn.toString()).append(".\n\nWas unable to send a DM to the user please inform the user manually.\n")
                .append(throwable.javaClass.simpleName).append(": ").append(throwable.message)
                .build()
        privateChannel.sendMessage(creatorMessage).queue()
    }
}
