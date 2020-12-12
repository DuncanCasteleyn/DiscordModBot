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

import be.duncanc.discordmodbot.bot.utils.nicknameAndUsername
import be.duncanc.discordmodbot.data.services.UserBlockService
import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.entities.ChannelType
import net.dv8tion.jda.api.entities.PrivateChannel
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import org.springframework.stereotype.Component
import java.time.format.DateTimeFormatter

/**
 * User info command.
 */
@Component
class UserInfo(
    userBlockService: UserBlockService
) : CommandModule(
    ALIASES,
    ARGUMENTATION_SYNTAX,
    DESCRIPTION,
    userBlockService = userBlockService
) {
    companion object {
        private val DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("dd-M-yyyy hh:mm:ss a O")
        private val ALIASES = arrayOf("UserInfo", "GetUserInfo")
        private const val ARGUMENTATION_SYNTAX = "[Username#Discriminator] (Without @)"
        private const val DESCRIPTION = "Prints out user information of the user given as argument"
    }

    /**
     * Do something with the event, command and arguments.
     *
     * @param event     A MessageReceivedEvent that came with the command
     * @param command   The command alias that was used to trigger this commandExec
     * @param arguments The arguments that where entered after the command alias
     */
    public override fun commandExec(event: MessageReceivedEvent, command: String, arguments: String?) {
        if (event.isFromType(ChannelType.TEXT)) {
            val privateChannel: PrivateChannel = event.author.openPrivateChannel().complete()
            if (arguments == null) {
                privateChannel.sendMessage("Please mention the user you want to get the dates from by using username#discriminator without @ sign, e.g.: \"Puck#5244\"\n")
                    .queue()
            } else if (!arguments.contains("#")) {
                privateChannel.sendMessage("Discriminator missing use username#discrimanator without @ sign, e.g.: \"Puck#5244\"")
                    .queue()
            } else {
                val searchTerms =
                    event.message.contentRaw.substring(command.length + 2).toLowerCase().split("#".toRegex())
                        .dropLastWhile { it.isEmpty }.toTypedArray()
                var targetFound = false
                for (member in event.guild.members) {
                    if (searchTerms[0] == member.user.name.toLowerCase() && searchTerms[1] == member.user.discriminator) {
                        //                                privateChannel.sendMessage("Dates from user " + member.toString() + "\n" +
                        //                                        "Guild join date: " + member.getJoinDate().format(DATE_TIME_FORMATTER) + "\n" +
                        //                                        "Account creation date: " + member.getUser().getCreationTime().format(DATE_TIME_FORMATTER)).queue();
                        val userDateMessage = EmbedBuilder()
                            .setAuthor(member.nicknameAndUsername, null, member.user.effectiveAvatarUrl)
                            .setThumbnail(member.user.effectiveAvatarUrl)
                            .setTitle("Guild: " + member.guild.name, null)
                            .addField("User id", member.user.id, false)
                            .addField("Discriminator", member.user.discriminator, false)
                            .addField("Online status", member.onlineStatus.name, false)
                            .addField("In voice channel?", member.voiceState?.inVoiceChannel().toString(), true)
                            .addField("Guild owner?", member.isOwner.toString(), true)
                            .addField("is a bot?", member.user.isBot.toString(), true)
                            .addField("Permissions", member.permissions.toString(), false)
                            .addField("Roles", member.roles.toString(), false)
                            .addField("Guild join date", member.timeJoined.format(DATE_TIME_FORMATTER), true)
                            .addField(
                                "Account creation date",
                                member.user.timeCreated.format(DATE_TIME_FORMATTER),
                                true
                            )
                            .build()
                        privateChannel.sendMessage(userDateMessage).queue()
                        targetFound = true
                        break
                    }
                }

                if (!targetFound) {
                    privateChannel.sendMessage("The specified user was not found.").queue()
                }
            }
        } else {
            event.channel.sendMessage("This command only works in a guild text channel.").queue()
        }
    }
}
