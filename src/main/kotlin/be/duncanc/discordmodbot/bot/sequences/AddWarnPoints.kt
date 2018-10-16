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
import be.duncanc.discordmodbot.bot.services.MuteRole
import be.duncanc.discordmodbot.bot.utils.JDALibHelper
import be.duncanc.discordmodbot.data.entities.GuildWarnPoints
import be.duncanc.discordmodbot.data.entities.GuildWarnPointsSettings
import be.duncanc.discordmodbot.data.entities.UserWarnPoints
import be.duncanc.discordmodbot.data.repositories.GuildWarnPointsRepository
import be.duncanc.discordmodbot.data.repositories.GuildWarnPointsSettingsRepository
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
class AddWarnPoints(
        val guildWarnPointsRepository: GuildWarnPointsRepository,
        val guildWarnPointsSettingsRepository: GuildWarnPointsSettingsRepository,
        val muteRole: MuteRole
) : CommandModule(
        arrayOf("AddWarnPoints", "AddPoints", "Warn"),
        "Mention a user",
        "This command is used to add points to a user, the user will be informed about this",
        requiredPermissions = *arrayOf(Permission.KICK_MEMBERS),
        ignoreWhitelist = true
) {
    override fun commandExec(event: MessageReceivedEvent, command: String, arguments: String?) {
        val guildId = event.guild.idLong
        if (command.equals("Warn", true) && !guildWarnPointsSettingsRepository.findById(guildId).orElse(GuildWarnPointsSettings(guildId)).overrideWarnCommand) {
            return
        }

        if (event.message.mentionedMembers.size != 1) {
            throw IllegalArgumentException("You need to mention 1 member.")
        }
        val member = event.message.mentionedMembers[0]
        if (event.member.canInteract(member)) {
            event.jda.addEventListener(AddPointsSequence(event.author, event.author.openPrivateChannel().complete(), member))
        } else {
            throw IllegalArgumentException("You can't interact with this member.")
        }
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
        private var expireDate: OffsetDateTime? = null

        init {
            channel.sendMessage("Please enter the reason for giving the user points.").queue()
        }

        override fun onMessageReceivedDuringSequence(event: MessageReceivedEvent) {
            val guildId = targetMember.guild.idLong
            val guildPointsSettings = guildWarnPointsSettingsRepository.findById(guildId).orElse(GuildWarnPointsSettings(guildId))
            if (guildPointsSettings.announceChannelId == null || guildPointsSettings.announceChannelId?.let { event.jda.getTextChannelById(it) == null } == true) {
                throw IllegalStateException("The announcement channel needs to be configured by a server administrator")
            }
            when {
                reason == null -> {
                    reason = event.message.contentDisplay
                    if (guildPointsSettings.maxPointsPerReason == 1) {
                        points = guildPointsSettings.maxPointsPerReason
                        channel.sendMessage("In how much days should these point(s) expire?").queue()
                    } else {
                        channel.sendMessage("Please enter the amount of points to assign. Your server administrator(s) has/have set a maximum of " + guildPointsSettings.maxPointsPerReason + " per reason").queue { super.addMessageToCleaner(it) }
                    }
                }
                points == null -> {
                    val inputPoints = event.message.contentRaw.toInt()
                    if (inputPoints > guildPointsSettings.maxPointsPerReason) {
                        throw IllegalArgumentException("This amount is above the maximum per reason")
                    }
                    points = inputPoints
                    channel.sendMessage("In how much days should these point(s) expire?").queue()
                }
                expireDate == null -> {
                    val days = event.message.contentRaw.toLong()
                    expireDate = OffsetDateTime.now().plusDays(days)
                    val muteText = try {
                        muteRole.getMuteRole(targetMember.guild)
                        ""
                    } catch (e: java.lang.IllegalStateException) {
                        " (Not configured)"
                    }
                    channel.sendMessage("Should an action be performed with this warn?\n0. None\n1. Mute$muteText\n2. Kick").queue()
                }
                else -> {
                    val action = event.message.contentRaw.toByte()
                    val guildWarnPoints = guildWarnPointsRepository.findById(GuildWarnPoints.GuildWarnPointsId(targetMember.user.idLong, targetMember.guild.idLong)).orElse(GuildWarnPoints(targetMember.user.idLong, targetMember.guild.idLong))
                    val userWarnPoints = UserWarnPoints(points = points, creatorId = user.idLong, reason = reason, expireDate = expireDate)
                    guildWarnPoints.points.add(userWarnPoints)
                    guildWarnPointsRepository.save(guildWarnPoints)
                    performChecks(guildWarnPoints, guildPointsSettings, targetMember)
                    val moderator = targetMember.guild.getMember(user)
                    logAddPoints(moderator, targetMember, reason!!, points!!, userWarnPoints.id, expireDate!!, action)
                    informUserAndModerator(moderator, targetMember, reason!!, guildWarnPoints.filterExpiredPoints().size, event.privateChannel, action)
                    val guild = targetMember.guild
                    when (action) {
                        1.toByte() -> guild.controller.addSingleRoleToMember(targetMember, muteRole.getMuteRole(guild)).reason(reason).queue()
                        2.toByte() -> guild.controller.kick(targetMember).reason(reason).queue()
                    }
                    super.destroy()
                }
            }
        }
    }

    private fun performChecks(guildWarnPoints: GuildWarnPoints, guildWarnPointsSettings: GuildWarnPointsSettings, targetMember: Member) {
        var points = 0
        val activatePoints = guildWarnPoints.points.asSequence().filter { it.expireDate?.isAfter(OffsetDateTime.now()) == true }.toCollection(mutableSetOf())
        activatePoints.forEach { points += it.points ?: 0 }
        if (points >= guildWarnPointsSettings.announcePointsSummaryLimit) {
            val guild = targetMember.guild
            val messageBuilder = MessageBuilder().append("@everyone ")
                    .append(targetMember.asMention)
                    .append(" has reached the limit of points set by your server administrator.\n\n")
                    .append("Summary of active points:")
            activatePoints.forEach {
                messageBuilder.append("\n\n").append(it.points).append(" point(s) added by ")
                        .append(JDALibHelper.getEffectiveNameAndUsername(guild.getMemberById(it.creatorId!!)))
                        .append(" on ").append(it.creationDate.format(JDALibHelper.messageTimeFormat)).append('\n')
                        .append("Reason: ").append(it.reason)
                        .append("\nExpires on: ").append(it.expireDate?.format(JDALibHelper.messageTimeFormat))
            }
            messageBuilder.buildAll(MessageBuilder.SplitPolicy.NEWLINE).forEach {
                guild.getTextChannelById(guildWarnPointsSettings.announceChannelId!!).sendMessage(it).queue()
            }
        }
    }

    private fun logAddPoints(moderator: Member, toInform: Member, reason: String, amount: Int, id: UUID, dateTime: OffsetDateTime, action: Byte) {
        val guildLogger = toInform.jda.registeredListeners.firstOrNull { it is GuildLogger } as GuildLogger?
        val logToChannel = guildLogger?.logger
        if (logToChannel != null) {
            val logEmbed = EmbedBuilder()
                    .setColor(Color.YELLOW)
                    .setTitle("Warn points added to user")
                    .addField("UUID", id.toString(), false)
                    .addField("User", JDALibHelper.getEffectiveNameAndUsername(toInform), true)
                    .addField("Moderator", JDALibHelper.getEffectiveNameAndUsername(moderator), true)
                    .addField("Amount", amount.toString(), false)
                    .addField("Reason", reason, false)
                    .addField("Expires", dateTime.format(JDALibHelper.messageTimeFormat), false)
            when (action) {
                1.toByte() -> logEmbed.addField("Punishment", "Mute", false)
                2.toByte() -> logEmbed.addField("Punishment", "Kick", false)
            }

            logToChannel.log(logEmbed, toInform.user, toInform.guild, null, GuildLogger.LogTypeAction.MODERATOR)
        }
    }

    private fun informUserAndModerator(moderator: Member, toInform: Member, reason: String, amountOfWarnings: Int, moderatorPrivateChannel: PrivateChannel, action: Byte) {
        val noteMessage = if (amountOfWarnings <= 1) {
            "Please watch your behavior in our server."
        } else {
            "You have received $amountOfWarnings warnings in recent history. Please watch your behaviour in our server."
        }
        val userWarning = EmbedBuilder()
                .setColor(Color.YELLOW)
                .setAuthor(JDALibHelper.getEffectiveNameAndUsername(moderator), null, moderator.user.effectiveAvatarUrl)
                .setTitle(moderator.guild.name + ": You have been warned by " + JDALibHelper.getEffectiveNameAndUsername(moderator), null)
                .addField("Reason", reason, false)
                .addField("Note", noteMessage, false)
        when (action) {
            1.toByte() -> userWarning.addField("Punishment", "Mute", false)
            2.toByte() -> userWarning.addField("Punishment", "Kick", false)
        }

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
                .append("Added warn points to ").append(toInform.toString()).append(".\n\nThe following message was sent to the user:")
                .setEmbed(informationMessage)
                .build()
        privateChannel.sendMessage(creatorMessage).queue()
    }

    private fun onFailToInformUser(privateChannel: PrivateChannel, toInform: Member, throwable: Throwable) {
        val creatorMessage = MessageBuilder()
                .append("Added warn points to ").append(toInform.toString()).append(".\n\nWas unable to send a DM to the user please inform the user manually.\n")
                .append(throwable.javaClass.simpleName).append(": ").append(throwable.message)
                .build()
        privateChannel.sendMessage(creatorMessage).queue()
    }
}