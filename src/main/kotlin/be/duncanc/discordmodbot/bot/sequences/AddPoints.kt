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
import be.duncanc.discordmodbot.bot.services.GuildLogger
import be.duncanc.discordmodbot.bot.utils.JDALibHelper
import be.duncanc.discordmodbot.data.embeddables.UserWarnPoints
import be.duncanc.discordmodbot.data.entities.GuildPointsSettings
import be.duncanc.discordmodbot.data.entities.UserGuildPoints
import be.duncanc.discordmodbot.data.repositories.GuildPointsSettingsRepository
import be.duncanc.discordmodbot.data.repositories.UserGuildPointsRepository
import net.dv8tion.jda.core.EmbedBuilder
import net.dv8tion.jda.core.MessageBuilder
import net.dv8tion.jda.core.Permission
import net.dv8tion.jda.core.entities.*
import net.dv8tion.jda.core.events.message.MessageReceivedEvent
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.awt.Color
import java.time.OffsetDateTime
import java.util.*

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
            private val targetMember: Member
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
            val guildId = targetMember.guild.idLong
            val guildPointsSettings = guildPointsSettingsRepository.findById(guildId).orElse(GuildPointsSettings(guildId))
            if (guildPointsSettings.announceChannelId == null) {
                throw IllegalStateException("The announcement channel needs to be configured by a server administrator")
            }
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
                    channel.sendMessage("In how much days should these point(s) expire?").queue { super.addMessageToCleaner(it) }
                }
                else -> {
                    val days = event.message.contentRaw.toLong()
                    val date = OffsetDateTime.now().plusDays(days)
                    val userGuildPoints = userGuildPointsRepository.findById(UserGuildPoints.UserGuildPointsId(targetMember.user.idLong, targetMember.guild.idLong)).orElse(UserGuildPoints(targetMember.user.idLong, targetMember.guild.idLong))
                    userGuildPoints.points.add(UserWarnPoints(points, user.idLong, reason, expireDate = date))
                    userGuildPointsRepository.save(userGuildPoints)
                    performChecks(userGuildPoints, guildPointsSettings, targetMember)
                    val moderator = targetMember.guild.getMember(user)
                    logAddPoints(moderator, targetMember, reason!!, points!!)
                    informUserAndModerator(moderator, targetMember, reason!!, points!!, event.privateChannel)
                    super.destroy()
                }
            }
        }
    }

    private fun performChecks(userGuildPoints: UserGuildPoints, guildPointsSettings: GuildPointsSettings, targetMember: Member) {
        var points = 0
        val activatePoints = userGuildPoints.points.filter { it.expireDate?.isAfter(OffsetDateTime.now()) == true }.toCollection(mutableSetOf())
        activatePoints.forEach { points += it.points ?: 0 }
        if (points >= guildPointsSettings.announcePointsSummaryLimit) {
            val guild = targetMember.guild
            val messageBuilder = MessageBuilder().append("@everyone ")
                    .append(targetMember.asMention)
                    .append(" has reached the limit of points set by your server administrator.\n\n")
                    .append("Summary of active points:")
            activatePoints.forEach {
                messageBuilder.append("\n\n").append(it.points).append(" point(s) added by ")
                        .append(JDALibHelper.getEffectiveNameAndUsername(guild.getMemberById(it.creatorId!!)))
                        .append(" on ").append(it.creationDate).append('\n')
                        .append("Reason: ").append(it.reason)
                        .append("\nExpires on: ").append(it.expireDate)
            }
            messageBuilder.buildAll(MessageBuilder.SplitPolicy.NEWLINE).forEach {
                guild.getTextChannelById(guildPointsSettings.announceChannelId!!).sendMessage(it).queue()
            }
        }
    }

    private fun logAddPoints(moderator: Member, toInform: Member, reason: String, amount: Int) {
        val guildLogger = toInform.jda.registeredListeners.firstOrNull { it is GuildLogger } as GuildLogger?
        val logToChannel = guildLogger?.logger
        if (logToChannel != null) {
            val logEmbed = EmbedBuilder()
                    .setColor(Color.YELLOW)
                    .setTitle("Points added to user")
                    .addField("UUID", UUID.randomUUID().toString(), false)
                    .addField("User", JDALibHelper.getEffectiveNameAndUsername(toInform), true)
                    .addField("Moderator", JDALibHelper.getEffectiveNameAndUsername(moderator), true)
                    .addField("Amount", amount.toString(), false)
                    .addField("Reason", reason, false)


            logToChannel.log(logEmbed, toInform.user, toInform.guild, null, GuildLogger.LogTypeAction.MODERATOR)
        }
    }

    private fun informUserAndModerator(moderator: Member, toInform: Member, reason: String, amount: Int, moderatorPrivateChannel: PrivateChannel) {
        val userWarning = EmbedBuilder()
                .setColor(Color.YELLOW)
                .setAuthor(JDALibHelper.getEffectiveNameAndUsername(moderator), null, moderator.user.effectiveAvatarUrl)
                .setTitle(moderator.guild.name + ": You have been given points by " + JDALibHelper.getEffectiveNameAndUsername(moderator), null)
                .addField("Amount", amount.toString(), false)
                .addField("Reason", reason, false)

        toInform.user.openPrivateChannel().queue(
                { privateChannelUserToWarn ->
                    privateChannelUserToWarn.sendMessage(userWarning.build()).queue(
                            { onSuccessfulInformUser(moderatorPrivateChannel, toInform, userWarning.build()) }
                    ) { throwable -> onFailToInformUser(moderatorPrivateChannel, toInform, throwable) }
                }
        ) { throwable -> onFailToInformUser(moderatorPrivateChannel, toInform, throwable) }
    }

    private fun onSuccessfulInformUser(privateChannel: PrivateChannel, toInform: Member, informationMessage: MessageEmbed) {
        val creatorMessage = MessageBuilder()
                .append("Informed ").append(toInform.toString()).append(".\n\nThe following message was sent to the user:")
                .setEmbed(informationMessage)
                .build()
        privateChannel.sendMessage(creatorMessage).queue()
    }

    private fun onFailToInformUser(privateChannel: PrivateChannel, toInform: Member, throwable: Throwable) {
        val creatorMessage = MessageBuilder()
                .append("Informed ").append(toInform.toString()).append(".\n\nWas unable to send a DM to the user please inform the user manually.\n")
                .append(throwable.javaClass.simpleName).append(": ").append(throwable.message)
                .build()
        privateChannel.sendMessage(creatorMessage).queue()
    }
}