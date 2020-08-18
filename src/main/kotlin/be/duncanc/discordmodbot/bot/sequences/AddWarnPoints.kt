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
import be.duncanc.discordmodbot.bot.utils.messageTimeFormat
import be.duncanc.discordmodbot.bot.utils.nicknameAndUsername
import be.duncanc.discordmodbot.data.entities.GuildWarnPoints
import be.duncanc.discordmodbot.data.entities.GuildWarnPointsSettings
import be.duncanc.discordmodbot.data.entities.UserWarnPoints
import be.duncanc.discordmodbot.data.repositories.GuildWarnPointsRepository
import be.duncanc.discordmodbot.data.repositories.GuildWarnPointsSettingsRepository
import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.MessageBuilder
import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.entities.*
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.awt.Color
import java.time.OffsetDateTime
import java.util.*
import java.util.concurrent.TimeUnit

@Component
class AddWarnPoints(
        val guildWarnPointsRepository: GuildWarnPointsRepository,
        val guildWarnPointsSettingsRepository: GuildWarnPointsSettingsRepository,
        val muteRole: MuteRole
) : CommandModule(
        arrayOf("AddWarnPoints", "AddPoints", "Warn"),
        "Mention a user",
        "This command is used to add points to a user, the user will be informed about this",
        requiredPermissions = arrayOf(Permission.KICK_MEMBERS),
        ignoreWhitelist = true
) {
    companion object {
        val illegalStateException = IllegalStateException("The announcement channel needs to be configured by a server administrator")
        val LOG: Logger = LoggerFactory.getLogger(AddWarnPoints::class.java)
        val reasonSizeLimit = 1024
    }

    override fun commandExec(event: MessageReceivedEvent, command: String, arguments: String?) {
        val guildId = event.guild.idLong
        val guildWarnPointsSettings = guildWarnPointsSettingsRepository.findById(guildId)
                .orElse(GuildWarnPointsSettings(guildId, announceChannelId = -1))
        if (command.equals("Warn", true) && !guildWarnPointsSettings.overrideWarnCommand
        ) {
            return
        }

        val userId = event.message.contentRaw.substring(command.length + 2).trimStart('<', '@', '!').trimEnd('>').toLong()
        event.jda.retrieveUserById(userId).queue(
                { targetUser ->
                    val member = event.guild.getMember(targetUser)
                    if (member == null || event.member?.canInteract(member) == true) {
                        event.author.openPrivateChannel().queue {
                            event.jda.addEventListener(
                                    AddPointsSequence(
                                            event.author,
                                            it,
                                            targetUser,
                                            event.guild
                                    )
                            )
                        }
                    } else {
                        event.channel.sendMessage("${event.author.asMention} You can't interact with this member.")
                                .queue { it.delete().queueAfter(1, TimeUnit.MINUTES) }
                    }
                },
                { t ->
                    LOG.error(
                            "Bot " + event.jda.selfUser.toString() + " on channel " + (if (event.channelType == ChannelType.TEXT) event.guild.toString() + " " else "") + event.channel.name + " failed executing " + event.message.contentStripped + " command from user " + event.author.toString(),
                            t
                    )
                    val exceptionMessage =
                            MessageBuilder().append("${event.author.asMention} Cannot complete action due to an error; see the message below for details.")
                                    .appendCodeBlock(t.javaClass.simpleName + ": " + t.message, "text").build()
                    event.channel.sendMessage(exceptionMessage).queue { it.delete().queueAfter(5, TimeUnit.MINUTES) }
                }
        )
    }

    @Transactional
    inner class AddPointsSequence(
            user: User,
            channel: MessageChannel,
            private val targetUser: User,
            private val guild: Guild
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
            val guildId = guild.idLong
            val guildPointsSettings =
                    guildWarnPointsSettingsRepository.findById(guildId).orElseThrow { throw illegalStateException }
            if (guildPointsSettings.announceChannelId.let {
                        event.jda.getTextChannelById(it) == null
                    }
            ) {
                throw illegalStateException
            }
            when {
                reason == null -> {
                    val contentDisplay = event.message.contentDisplay
                    if(contentDisplay.length > reasonSizeLimit) {
                        throw IllegalArgumentException("Reasons cannot exceed $reasonSizeLimit characters")
                    }
                    reason = contentDisplay
                    if (guildPointsSettings.maxPointsPerReason == 1) {
                        points = guildPointsSettings.maxPointsPerReason
                        askForExpireTime()
                    } else {
                        channel.sendMessage("Please enter the amount of points to assign. Your server administrator(s) has/have set a maximum of " + guildPointsSettings.maxPointsPerReason + " per reason")
                                .queue { super.addMessageToCleaner(it) }
                    }
                }
                points == null -> {
                    val inputPoints = event.message.contentRaw.toInt()
                    if (inputPoints > guildPointsSettings.maxPointsPerReason) {
                        throw IllegalArgumentException("This amount is above the maximum per reason")
                    }
                    points = inputPoints
                    askForExpireTime()
                }
                expireDate == null -> {
                    val days = event.message.contentRaw.toLong()
                    expireDate = OffsetDateTime.now().plusDays(days)
                    val muteText = try {
                        muteRole.getMuteRole(guild)
                        ""
                    } catch (e: java.lang.IllegalStateException) {
                        " (Not configured)"
                    }
                    if (!guild.isMember(user)) {
                        processSequence(event, guildPointsSettings)
                        return
                    }
                    channel.sendMessage("Should an action be performed with this warn?\n0. None\n1. Mute$muteText\n2. Kick")
                            .queue()
                }
                else -> {
                    processSequence(event, guildPointsSettings)
                }
            }
        }

        private fun askForExpireTime() {
            channel.sendMessage("In how much days should these point(s) expire?").queue()
        }

        private fun processSequence(event: MessageReceivedEvent, guildPointsSettings: GuildWarnPointsSettings) {
            val action = event.message.contentRaw.toByte()
            val guildWarnPoints = guildWarnPointsRepository.findById(
                    GuildWarnPoints.GuildWarnPointsId(
                            targetUser.idLong,
                            guild.idLong
                    )
            ).orElse(GuildWarnPoints(targetUser.idLong, guild.idLong))
            val userWarnPoints = UserWarnPoints(
                    points = points!!,
                    creatorId = user.idLong,
                    reason = reason!!,
                    expireDate = expireDate!!
            )
            guildWarnPoints.points.add(userWarnPoints)
            guildWarnPointsRepository.save(guildWarnPoints)
            performChecks(guildWarnPoints, guildPointsSettings, targetUser, guild)
            val moderator = guild.getMember(user)!!
            logAddPoints(moderator, targetUser, reason!!, points!!, userWarnPoints.id, expireDate!!, action)
            val member = guild.getMember(targetUser)
            if (member != null) {
                informUserAndModerator(
                        moderator,
                        member,
                        reason!!,
                        guildWarnPoints.filterExpiredPoints().size,
                        event.privateChannel,
                        action
                )


                when (action) {
                    1.toByte() -> guild.addRoleToMember(
                            member,
                            muteRole.getMuteRole(guild)
                    ).reason(reason).queue()
                    2.toByte() -> {

                        guild.kick(member).reason(reason).queue()
                    }
                }
            }
            super.destroy()
        }
    }

    private fun performChecks(
            guildWarnPoints: GuildWarnPoints,
            guildWarnPointsSettings: GuildWarnPointsSettings,
            user: User,
            guild: Guild
    ) {
        var points = 0
        val activatePoints =
                guildWarnPoints.points.asSequence().filter { it.expireDate.isAfter(OffsetDateTime.now()) }
                        .toCollection(mutableSetOf())
        activatePoints.forEach { points += it.points }
        if (points >= guildWarnPointsSettings.announcePointsSummaryLimit) {
            val messageBuilder = MessageBuilder().append("@everyone ")
                    .append(user.asMention)
                    .append(" has reached the limit of points set by your server administrator.\n\n")
                    .append("Summary of active points:")
            activatePoints.forEach {
                messageBuilder.append("\n\n").append(it.points).append(" point(s) added by ")
                        .append(guild.getMemberById(it.creatorId)?.nicknameAndUsername)
                        .append(" on ").append(it.creationDate.format(messageTimeFormat)).append('\n')
                        .append("Reason: ").append(it.reason)
                        .append("\nExpires on: ").append(it.expireDate.format(messageTimeFormat))
            }
            messageBuilder.buildAll(MessageBuilder.SplitPolicy.NEWLINE).forEach {
                guild.getTextChannelById(guildWarnPointsSettings.announceChannelId)?.sendMessage(it)?.queue()
            }
        }
    }

    private fun logAddPoints(
            moderator: Member,
            toInform: User,
            reason: String,
            amount: Int,
            id: UUID,
            dateTime: OffsetDateTime,
            action: Byte
    ) {
        val guild = moderator.guild
        val guildLogger = toInform.jda.registeredListeners.firstOrNull { it is GuildLogger } as GuildLogger?
        if (guildLogger != null) {
            val logEmbed = EmbedBuilder()
                    .setColor(Color.YELLOW)
                    .setTitle("Warn points added to user")
                    .addField("UUID", id.toString(), false)
                    .addField("User", guild.getMember(toInform)?.nicknameAndUsername ?: toInform.name, true)
                    .addField("Moderator", moderator.nicknameAndUsername, true)
                    .addField("Amount", amount.toString(), false)
                    .addField("Reason", reason, false)
                    .addField("Expires", dateTime.format(messageTimeFormat), false)
            when (action) {
                1.toByte() -> logEmbed.addField("Punishment", "Mute", false)
                2.toByte() -> logEmbed.addField("Punishment", "Kick", false)
            }

            guildLogger.log(logEmbed, toInform, guild, null, GuildLogger.LogTypeAction.MODERATOR)
        }
    }

    private fun informUserAndModerator(
            moderator: Member,
            toInform: Member,
            reason: String,
            amountOfWarnings: Int,
            moderatorPrivateChannel: PrivateChannel,
            action: Byte
    ) {
        val noteMessage = if (amountOfWarnings <= 1) {
            "Please watch your behavior in our server."
        } else {
            "You have received $amountOfWarnings warnings in recent history. Please watch your behaviour in our server."
        }
        val userWarning = EmbedBuilder()
                .setColor(Color.YELLOW)
                .setAuthor(moderator.nicknameAndUsername, null, moderator.user.effectiveAvatarUrl)
                .setTitle("${moderator.guild.name}: You have been warned by ${moderator.nicknameAndUsername}", null)
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

    private fun onSuccessfulInformUser(
            privateChannel: PrivateChannel,
            toInform: Member,
            informationMessage: MessageEmbed
    ) {
        val creatorMessage = MessageBuilder()
                .append("Added warn points to ").append(toInform.toString())
                .append(".\n\nThe following message was sent to the user:")
                .setEmbed(informationMessage)
                .build()
        privateChannel.sendMessage(creatorMessage).queue()
    }

    private fun onFailToInformUser(privateChannel: PrivateChannel, toInform: Member, throwable: Throwable) {
        val creatorMessage = MessageBuilder()
                .append("Added warn points to ").append(toInform.toString())
                .append(".\n\nWas unable to send a DM to the user please inform the user manually.\n")
                .append(throwable.javaClass.simpleName).append(": ").append(throwable.message)
                .build()
        privateChannel.sendMessage(creatorMessage).queue()
    }
}
