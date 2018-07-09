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

package be.duncanc.discordmodbot.bot.sequences

import be.duncanc.discordmodbot.bot.commands.CommandModule
import be.duncanc.discordmodbot.data.embeddables.UserPoints
import be.duncanc.discordmodbot.data.entities.GuildPointsSettings
import be.duncanc.discordmodbot.data.entities.UserGuildPoints
import be.duncanc.discordmodbot.data.repositories.GuildPointsSettingsRepository
import be.duncanc.discordmodbot.data.repositories.UserGuildPointsRepository
import net.dv8tion.jda.core.Permission
import net.dv8tion.jda.core.entities.Member
import net.dv8tion.jda.core.entities.MessageChannel
import net.dv8tion.jda.core.entities.User
import net.dv8tion.jda.core.events.message.MessageReceivedEvent
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.OffsetDateTime

@Component
class AddPoints(
        val userGuildPointsRepository: UserGuildPointsRepository,
        val guildPointsSettingsRepository: GuildPointsSettingsRepository
) : CommandModule(
        arrayOf("AddPoints"),
        "Mention a user",
        "This command is used to add points to a user, the user will be informed about this",
        requiredPermissions = *arrayOf(Permission.KICK_MEMBERS)
) {
    override fun commandExec(event: MessageReceivedEvent, command: String, arguments: String?) {
        if (event.message.mentionedMembers.size != 1) {
            throw IllegalArgumentException("You need to mention 1 member.")
        }
        event.jda.addEventListener(AddPointsSequence(event.author, event.author.openPrivateChannel().complete(), event.message.mentionedMembers[0]))
    }

    @Transactional
    inner class AddPointsSequence(
            user: User,
            channel: MessageChannel,
            private val targetUser: Member
    ) : Sequence(
            user,
            channel
    ) {
        private var reason: String? = null
        private var points: Int? = null

        init {
            channel.sendMessage("Please enter the reason for giving the user points.").queue { super.addMessageToCleaner(it) }
        }

        override fun onMessageReceivedDuringSequence(event: MessageReceivedEvent) {
            val guildId = targetUser.guild.idLong
            val guildPointsSettings = guildPointsSettingsRepository.findById(guildId).orElse(GuildPointsSettings(guildId))
            when {
                reason == null -> {
                    reason = event.message.contentDisplay
                    channel.sendMessage("Please enter the amount of points to assign. Your server administrator(s) has/have set a maximum of " + guildPointsSettings.maxPointsPerReason + " per reason").queue { super.addMessageToCleaner(it) }
                }
                points == null -> {
                    val inputPoints = event.message.contentRaw.toInt()
                    if (inputPoints > guildPointsSettings.maxPointsPerReason) {
                        throw IllegalArgumentException("This amount is above the maximum per reason")
                    }
                    points = inputPoints
                    channel.sendMessage("In how much days should these point(s) expire?")
                }
                else -> {
                    val days = event.message.contentRaw.toLong()
                    val date = OffsetDateTime.now()
                    date.plusDays(days)
                    val userGuildPoints = userGuildPointsRepository.findById(UserGuildPoints.UserGuildPointsId(targetUser.user.idLong, targetUser.guild.idLong)).orElse(UserGuildPoints(targetUser.user.idLong, targetUser.guild.idLong))
                    userGuildPoints.points.add(UserPoints(points, user.idLong, reason, expireDate = date))
                    userGuildPointsRepository.save(userGuildPoints)
                    performChecks(userGuildPoints)
                    super.destroy()
                }
            }
        }
    }

    private fun performChecks(userGuildPoints: UserGuildPoints) {
        TODO("WIP")
    }
}