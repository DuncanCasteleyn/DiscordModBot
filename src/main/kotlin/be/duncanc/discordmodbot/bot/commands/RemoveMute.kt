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
import net.dv8tion.jda.core.MessageBuilder
import net.dv8tion.jda.core.Permission
import net.dv8tion.jda.core.entities.ChannelType
import net.dv8tion.jda.core.entities.Member
import net.dv8tion.jda.core.entities.MessageEmbed
import net.dv8tion.jda.core.entities.PrivateChannel
import net.dv8tion.jda.core.events.message.MessageReceivedEvent
import java.awt.Color

/**
 * Created by Duncan on 24/02/2017.
 *
 *
 * This class creates an RemoveMute command that is logged.
 */
/**
 * Constructor for abstract class
 */
object RemoveMute : CommandModule(arrayOf("Unmute", "RemoveMute"), "[User mention] [Reason~]", "This command will remove a mute from a user and log it to the log channel.", true, true) {

    /**
     * Do something with the event, command and arguments.
     *
     * @param event     A MessageReceivedEvent that came with the command
     * @param command   The command alias that was used to trigger this commandExec
     * @param arguments The arguments that where entered after the command alias
     */
    public override fun commandExec(event: MessageReceivedEvent, command: String, arguments: String?) {
        event.author.openPrivateChannel().queue(
                { privateChannel -> commandExec(event, arguments, privateChannel) }
        ) { commandExec(event, arguments, null as PrivateChannel?) }
    }

    private fun commandExec(event: MessageReceivedEvent, arguments: String?, privateChannel: PrivateChannel?) {
        if (!event.isFromType(ChannelType.TEXT)) {
            privateChannel?.sendMessage("This command only works in a guild.")?.queue()
        } else if (!event.member.hasPermission(Permission.MANAGE_ROLES)) {
            privateChannel?.sendMessage(event.author.asMention + " you need manage roles permission to remove a mute!")?.queue()
        } else if (event.message.mentionedUsers.size < 1) {
            privateChannel?.sendMessage("Illegal argumentation, you need to mention a user that is still in the server.")?.queue()
        } else {
            val reason: String
            try {
                reason = arguments!!.substring(arguments.split(" ".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()[0].length + 1)
            } catch (e: IndexOutOfBoundsException) {
                throw IllegalArgumentException("No reason provided for this action.")
            }

            val toRemoveMute = event.guild.getMember(event.message.mentionedUsers[0])
            if (!toRemoveMute.roles.contains(event.guild.getRoleById("221678882342830090"))) {
                throw UnsupportedOperationException("Can't remove a mute from a user that is not muted.")
            }
            event.guild.controller.removeRolesFromMember(toRemoveMute, event.guild.getRoleById("221678882342830090")).reason(reason).queue({
                val guildLogger = event.jda.registeredListeners.firstOrNull { it is GuildLogger } as GuildLogger?
                val logToChannel = guildLogger?.logger
                if (logToChannel != null) {
                    val logEmbed = EmbedBuilder()
                            .setColor(Color.GREEN)
                            .setTitle("User's mute was removed", null)
                            .addField("User", JDALibHelper.getEffectiveNameAndUsername(toRemoveMute), true)
                            .addField("Moderator", JDALibHelper.getEffectiveNameAndUsername(event.member), true)
                            .addField("Reason", reason, false)

                    logToChannel.log(logEmbed, toRemoveMute.user, event.guild, null, GuildLogger.LogTypeAction.MODERATOR)
                }
                val muteRemoveNotification = EmbedBuilder()
                        .setColor(Color.green)
                        .setAuthor(JDALibHelper.getEffectiveNameAndUsername(event.member), null, event.author.effectiveAvatarUrl)
                        .setTitle(event.guild.name + ": Your mute has been removed by " + JDALibHelper.getEffectiveNameAndUsername(event.member), null)
                        .addField("Reason", reason, false)
                        .build()

                toRemoveMute.user.openPrivateChannel().queue(
                        { privateChannelUserToRemoveMute ->
                            privateChannelUserToRemoveMute.sendMessage(muteRemoveNotification).queue(
                                    { onSuccessfulInformUser(privateChannel, toRemoveMute, muteRemoveNotification) }
                            ) { throwable -> onFailToInformUser(privateChannel, toRemoveMute, throwable) }
                        }
                ) { throwable -> onFailToInformUser(privateChannel, toRemoveMute, throwable) }
            }) { throwable ->
                if (privateChannel == null) {
                    return@queue
                }

                val creatorMessage = MessageBuilder()
                        .append("Failed removing mute ").append(toRemoveMute.toString()).append(".\n")
                        .append(throwable.javaClass.simpleName).append(": ").append(throwable.message)
                        .build()
                privateChannel.sendMessage(creatorMessage).queue()
            }
        }
    }

    private fun onSuccessfulInformUser(privateChannel: PrivateChannel?, toRemoveMute: Member, muteRemoveNotification: MessageEmbed) {
        if (privateChannel == null) {
            return
        }

        val creatorMessage = MessageBuilder()
                .append("Removed mute from ").append(toRemoveMute.toString()).append(".\n\nThe following message was sent to the user:")
                .setEmbed(muteRemoveNotification)
                .build()
        privateChannel.sendMessage(creatorMessage).queue()
    }

    private fun onFailToInformUser(privateChannel: PrivateChannel?, toRemoveMute: Member, throwable: Throwable) {
        if (privateChannel == null) {
            return
        }

        val creatorMessage = MessageBuilder()
                .append("Removed mute from ").append(toRemoveMute.toString()).append(".\n\nWas unable to send a DM to the user please inform the user manually.\n")
                .append(throwable.javaClass.simpleName).append(": ").append(throwable.message)
                .build()
        privateChannel.sendMessage(creatorMessage).queue()
    }
}
