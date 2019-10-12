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
import net.dv8tion.jda.api.entities.Member
import net.dv8tion.jda.api.entities.MessageEmbed
import net.dv8tion.jda.api.entities.PrivateChannel
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import org.springframework.stereotype.Component
import java.awt.Color

/**
 * This class creates an RemoveMute command that is logged.
 */
@Component
class RemoveMute : CommandModule(
    arrayOf("Unmute", "RemoveMute"),
    "[User mention] [Reason~]",
    "This command will remove a mute from a user and log it to the log channel.",
    true,
    true
) {

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
        } else if (event.member?.hasPermission(Permission.MANAGE_ROLES) != true) {
            privateChannel?.sendMessage(event.author.asMention + " you need manage roles permission to remove a mute!")
                ?.queue()
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

            val toRemoveMute = event.guild.getMember(event.message.mentionedUsers[0])
            if (toRemoveMute?.roles?.contains(event.guild.getRoleById("221678882342830090")) != true) { // TODO fix this hard coded role
                throw UnsupportedOperationException("Can't remove a mute from a user that is not muted.")
            }
            event.guild.removeRoleFromMember(toRemoveMute, event.guild.getRoleById("221678882342830090")!!)
                    .queue({ _ ->
                    val guildLogger = event.jda.registeredListeners.firstOrNull { it is GuildLogger } as GuildLogger?
                    if (guildLogger != null) {
                        val logEmbed = EmbedBuilder()
                            .setColor(Color.GREEN)
                            .setTitle("User's mute was removed", null)
                            .addField("User", toRemoveMute.nicknameAndUsername, true)
                                .addField("Moderator", event.member!!.nicknameAndUsername, true)
                            .addField("Reason", reason, false)

                        guildLogger.log(
                            logEmbed,
                            toRemoveMute.user,
                            event.guild,
                            null,
                            GuildLogger.LogTypeAction.MODERATOR
                        )
                    }
                    val muteRemoveNotification = EmbedBuilder()
                        .setColor(Color.green)
                            .setAuthor(event.member!!.nicknameAndUsername, null, event.author.effectiveAvatarUrl)
                        .setTitle(
                                event.guild.name + ": Your mute has been removed by " + event.member!!.nicknameAndUsername,
                            null
                        )
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

    private fun onSuccessfulInformUser(
        privateChannel: PrivateChannel?,
        toRemoveMute: Member,
        muteRemoveNotification: MessageEmbed
    ) {
        if (privateChannel == null) {
            return
        }

        val creatorMessage = MessageBuilder()
            .append("Removed mute from ").append(toRemoveMute.toString())
            .append(".\n\nThe following message was sent to the user:")
            .setEmbed(muteRemoveNotification)
            .build()
        privateChannel.sendMessage(creatorMessage).queue()
    }

    private fun onFailToInformUser(privateChannel: PrivateChannel?, toRemoveMute: Member, throwable: Throwable) {
        if (privateChannel == null) {
            return
        }

        val creatorMessage = MessageBuilder()
            .append("Removed mute from ").append(toRemoveMute.toString())
            .append(".\n\nWas unable to send a DM to the user please inform the user manually.\n")
            .append(throwable.javaClass.simpleName).append(": ").append(throwable.message)
            .build()
        privateChannel.sendMessage(creatorMessage).queue()
    }
}
