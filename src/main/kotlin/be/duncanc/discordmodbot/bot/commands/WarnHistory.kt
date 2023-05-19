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

import be.duncanc.discordmodbot.bot.utils.messageTimeFormat
import be.duncanc.discordmodbot.bot.utils.nicknameAndUsername
import be.duncanc.discordmodbot.data.entities.GuildWarnPoints
import be.duncanc.discordmodbot.data.repositories.jpa.GuildWarnPointsRepository
import be.duncanc.discordmodbot.data.services.UserBlockService
import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.entities.User
import net.dv8tion.jda.api.entities.channel.ChannelType
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import net.dv8tion.jda.api.utils.SplitUtil
import org.springframework.stereotype.Component
import java.util.concurrent.TimeUnit

@Component
class WarnHistory(
    val guildWarnPointsRepository: GuildWarnPointsRepository,
    userBlockService: UserBlockService
) : CommandModule(
    arrayOf("WarnHistory"),
    null,
    null,
    userBlockService = userBlockService
) {
    override fun commandExec(event: MessageReceivedEvent, command: String, arguments: String?) {
        if (!event.isFromType(ChannelType.TEXT)) {
            throw IllegalArgumentException("This command can only be used in a guild/server channel.")
        }
        if (event.message.mentions.members.size == 1 && event.member?.hasPermission(Permission.KICK_MEMBERS) == true) {
            val requestedUserPoints = guildWarnPointsRepository.findById(
                GuildWarnPoints.GuildWarnPointsId(
                    event.message.mentions.members[0].idLong,
                    event.guild.idLong
                )
            )
            if (requestedUserPoints.isPresent) {
                informUserOfPoints(event.author, requestedUserPoints.get(), event.guild, true)
                event.guildChannel.sendMessage("The list of points the user collected has been send in a private message for privacy reason")
                    .queue(cleanUp())
            } else {
                event.guildChannel.sendMessage("The user has not received any points.").queue(cleanUp())
            }
        } else {
            val userPoints = guildWarnPointsRepository.findById(
                GuildWarnPoints.GuildWarnPointsId(
                    event.author.idLong,
                    event.guild.idLong
                )
            )
            if (userPoints.isPresent) {
                informUserOfPoints(event.author, userPoints.get(), event.guild, false)
                event.guildChannel.sendMessage("Your list of points has been send in a private message to you for privacy reasons, if you didn't receive any messages make sure you have enabled dm from server members on this server before executing this command.")
                    .queue(cleanUp())
            } else {
                event.guildChannel.sendMessage("You haven't received any points. Good job!").queue(cleanUp())
            }
        }
    }

    private fun cleanUp(): (Message) -> Unit =
        { it.delete().queueAfter(1, TimeUnit.MINUTES) }

    private fun informUserOfPoints(user: User, warnPoints: GuildWarnPoints, guild: Guild, moderator: Boolean) {
        user.openPrivateChannel().queue { privateChannel ->
            val message = StringBuilder()
            message.append("Warning history for ").append(user.jda.getUserById(warnPoints.userId)).append(':')
            warnPoints.points.forEach {
                message.append("\n\n")
                if (moderator) {
                    message.append(it.points).append(" point(s)")
                } else {
                    message.append("Warned")
                }
                message.append(" on ").append(it.creationDate.format(messageTimeFormat)).append(" by ")
                    .append(guild.getMemberById(it.creatorId)?.nicknameAndUsername)
                    .append("\n\nReason: ").append(it.reason)
                if (moderator) {
                    message.append("\n\nExpires: ").append(it.expireDate.format(messageTimeFormat))
                }
            }
            val messages = SplitUtil.split(message.toString(), Message.MAX_CONTENT_LENGTH, SplitUtil.Strategy.NEWLINE)

            messages.forEach {
                privateChannel.sendMessage(it).queue()
            }
        }
    }
}
