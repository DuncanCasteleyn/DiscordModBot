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
import be.duncanc.discordmodbot.bot.services.MuteRoles
import be.duncanc.discordmodbot.bot.utils.JDALibHelper
import net.dv8tion.jda.core.EmbedBuilder
import net.dv8tion.jda.core.MessageBuilder
import net.dv8tion.jda.core.Permission
import net.dv8tion.jda.core.entities.ChannelType
import net.dv8tion.jda.core.entities.Member
import net.dv8tion.jda.core.entities.MessageEmbed
import net.dv8tion.jda.core.entities.PrivateChannel
import net.dv8tion.jda.core.events.message.MessageReceivedEvent
import net.dv8tion.jda.core.exceptions.PermissionException
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.ApplicationContext
import org.springframework.stereotype.Component
import java.awt.Color

/**
 * This class creates a mute command that will be logged.
 */
@Component
class Mute private constructor() : CommandModule(arrayOf("Mute"), "[User mention] [Reason~]", "This command will put a user in the muted group and log the mute to the log channel.", true, true) {

    @Autowired
    private lateinit var applicationContext: ApplicationContext

    public override fun commandExec(event: MessageReceivedEvent, command: String, arguments: String?) {
        event.author.openPrivateChannel().queue(
                { privateChannel -> commandExec(event, arguments, privateChannel) }
        ) { commandExec(event, arguments, null as PrivateChannel?) }
    }

    private fun commandExec(event: MessageReceivedEvent, arguments: String?, privateChannel: PrivateChannel?) {
        if (!event.isFromType(ChannelType.TEXT)) {
            privateChannel?.sendMessage("This command only works in a guild.")?.queue()
        } else if (!event.member.hasPermission(Permission.MANAGE_ROLES)) {
            privateChannel?.sendMessage(event.author.asMention + " you need manage roles permission to mute!")?.queue()
        } else if (event.message.mentionedUsers.size < 1) {
            privateChannel?.sendMessage("Illegal argumentation, you need to mention a user that is still in the server.")?.queue()
        } else {
            val reason: String
            try {
                reason = arguments!!.substring(arguments.split(" ".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()[0].length + 1)
            } catch (e: IndexOutOfBoundsException) {
                throw IllegalArgumentException("No reason provided for this action.")
            }

            val toMute = event.guild.getMember(event.message.mentionedUsers[0])
            if (!event.member.canInteract(toMute)) {
                throw PermissionException("You can't interact with this member")
            }
            event.guild.controller.addRolesToMember(toMute, applicationContext.getBean(MuteRoles::class.java).getMuteRole(event.guild)).reason(reason).queue({
                val guildLogger = event.jda.registeredListeners.firstOrNull { it is GuildLogger } as GuildLogger?
                val logToChannel = guildLogger?.logger
                if (logToChannel != null) {
                    val serializableCaseResult = GuildLogger.getCaseNumberSerializable(event.guild.idLong)
                    val logEmbed = EmbedBuilder()
                            .setColor(Color.YELLOW)
                            .setTitle("User muted | Case: $serializableCaseResult")
                            .addField("User", JDALibHelper.getEffectiveNameAndUsername(toMute), true)
                            .addField("Moderator", JDALibHelper.getEffectiveNameAndUsername(event.member), true)
                            .addField("Reason", reason, false)

                    logToChannel.log(logEmbed, toMute.user, event.guild, null, GuildLogger.LogTypeAction.MODERATOR)
                }
                val userMuteWarning = EmbedBuilder()
                        .setColor(Color.YELLOW)
                        .setAuthor(JDALibHelper.getEffectiveNameAndUsername(event.member), null, event.author.effectiveAvatarUrl)
                        .setTitle(event.guild.name + ": You have been muted by " + JDALibHelper.getEffectiveNameAndUsername(event.member))
                        .addField("Reason", reason, false)

                toMute.user.openPrivateChannel().queue(
                        { privateChannelUserToMute ->
                            privateChannelUserToMute.sendMessage(userMuteWarning.build()).queue(
                                    { onSuccessfulInformUser(privateChannel, toMute, userMuteWarning.build()) }
                            ) { throwable -> onFailToInformUser(privateChannel, toMute, throwable) }
                        }
                ) { throwable -> onFailToInformUser(privateChannel, toMute, throwable) }
                applicationContext.getBean(ModNotes::class.java).addNote(reason, ModNotes.NoteType.MUTE, toMute.user.idLong, event.guild.idLong, event.author.idLong)

            }) { throwable ->
                if (privateChannel == null) {
                    return@queue
                }

                val creatorMessage = MessageBuilder()
                        .append("Failed muting ").append(toMute.toString()).append(".\n")
                        .append(throwable.javaClass.simpleName).append(": ").append(throwable.message)
                        .build()
                privateChannel.sendMessage(creatorMessage).queue()
            }
        }
    }

    /**
     * Will be called when the user was successfully informed about his mute.
     *
     * @param privateChannel  The PrivateChannel of the moderator that executed this command.
     * @param toMute          The user that is going to be muted.
     * @param userMuteWarning The warning that was send to the user.
     */
    private fun onSuccessfulInformUser(privateChannel: PrivateChannel?, toMute: Member, userMuteWarning: MessageEmbed) {
        if (privateChannel == null) {
            return
        }

        val creatorMessage = MessageBuilder()
                .append("Muted ").append(toMute.toString()).append(".\n\nThe following message was sent to the user:")
                .setEmbed(userMuteWarning)
                .build()
        privateChannel.sendMessage(creatorMessage).queue()
    }

    /**
     * Will be called when informing the user about his mute failed.
     *
     * @param privateChannel the PrivateChannel of the moderator that executed this command
     * @param toMute         The user that is going to be muted.
     * @param throwable      The error that occurred, when trying to send a message.
     */
    private fun onFailToInformUser(privateChannel: PrivateChannel?, toMute: Member, throwable: Throwable) {
        if (privateChannel == null) {
            return
        }

        val creatorMessage = MessageBuilder()
                .append("Muted ").append(toMute.toString()).append(".\n\nWas unable to send a DM to the user please inform the user manually.\n")
                .append(throwable.javaClass.simpleName).append(": ").append(throwable.message)
                .build()
        privateChannel.sendMessage(creatorMessage).queue()
    }
}
