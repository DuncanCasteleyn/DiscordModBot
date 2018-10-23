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

package be.duncanc.discordmodbot.bot.services

import be.duncanc.discordmodbot.bot.commands.CommandModule
import be.duncanc.discordmodbot.bot.sequences.Sequence
import be.duncanc.discordmodbot.bot.utils.JDALibHelper
import be.duncanc.discordmodbot.data.entities.ActivityReportSettings
import be.duncanc.discordmodbot.data.repositories.ActivityReportSettingsRepository
import net.dv8tion.jda.core.JDA
import net.dv8tion.jda.core.MessageBuilder
import net.dv8tion.jda.core.Permission
import net.dv8tion.jda.core.entities.Member
import net.dv8tion.jda.core.entities.MessageChannel
import net.dv8tion.jda.core.entities.TextChannel
import net.dv8tion.jda.core.entities.User
import net.dv8tion.jda.core.events.ReadyEvent
import net.dv8tion.jda.core.events.ShutdownEvent
import net.dv8tion.jda.core.events.message.MessageReceivedEvent
import net.dv8tion.jda.core.exceptions.PermissionException
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Duration
import java.time.OffsetDateTime
import java.util.concurrent.TimeUnit

@Component
class WeeklyActivityReport(
        private val activityReportSettingsRepository: ActivityReportSettingsRepository
) : CommandModule(
        arrayOf("WeeklyActivitySettings"),
        null,
        "Allows you to configure weekly reports on ammount of message per channel for certain users (in a role)"
) {
    companion object {
        val LOG: Logger = LoggerFactory.getLogger(WeeklyActivityReport::class.java)
    }

    val instances = HashSet<JDA>()

    override fun onReady(event: ReadyEvent) {
        instances.add(event.jda)
    }

    override fun onShutdown(event: ShutdownEvent) {
        instances.remove(event.jda)
    }

    override fun commandExec(event: MessageReceivedEvent, command: String, arguments: String?) {
        if (event.member.hasPermission(Permission.ADMINISTRATOR)) {
            event.jda.addEventListener(WeeklyReportConfigurationSequence(event.author, event.channel))
        } else {
            throw PermissionException("You need administrator permissions.")
        }
    }

    @Scheduled(cron = "0 0 0 * * MON")
    @Transactional(readOnly = true)
    fun sendReports() {
        val statsCollectionStartTime = OffsetDateTime.now()
        activityReportSettingsRepository.findAll().forEach { reportSettings ->
            val guild = reportSettings.guildId?.let { guildId ->
                instances.stream().filter { jda ->
                    jda.getGuildById(guildId) != null
                }.findFirst().orElse(null)?.getGuildById(guildId)
            }
            if (guild != null) {
                val textChannel = reportSettings.reportChannel?.let { guild.getTextChannelById(it) }
                if (textChannel != null) {
                    val trackedMembers = HashSet<Member>()
                    reportSettings.trackedRoleOrMember.forEach {
                        val role = guild.getRoleById(it)
                        if (role != null) {
                            val members = guild.getMembersWithRoles(role)
                            trackedMembers.addAll(members)
                        } else {
                            val member = guild.getMemberById(it)
                            trackedMembers.add(member)
                        }
                    }
                    if (trackedMembers.isEmpty()) {
                        return@forEach
                    }
                    val stats = HashMap<TextChannel, HashMap<Member, Long>>()
                    guild.textChannels.forEach {
                        val channelStats = HashMap<Member, Long>()
                        trackedMembers.forEach { trackedMember -> channelStats[trackedMember] = 0L }
                        try {
                            for (message in it.iterableHistory) {
                                if (trackedMembers.contains(message.member)) {
                                    channelStats[message.member] = (channelStats[message.member] ?: 0L) + 1L
                                }
                                if (Duration.between(message.creationTime, statsCollectionStartTime).toDays() >= 7) {
                                    break
                                }
                            }
                            stats[it] = channelStats
                        } catch (e: PermissionException) {
                            LOG.warn("Insufficient permissions to retrieve history from $it")
                        }
                    }
                    val message = MessageBuilder()
                    message.append("**Message statistics of the past 7 days**\n")
                    stats.forEach { channel, channelStats ->
                        message.append("\n***${channel.asMention}***\n\n")
                        channelStats.forEach { member, count ->
                            message.append("${JDALibHelper.getEffectiveNameAndUsername(member)}: $count\n")
                        }
                    }
                    message.buildAll(MessageBuilder.SplitPolicy.NEWLINE).forEach {
                        textChannel.sendMessage(it).queue()
                    }
                } else {
                    LOG.warn("The text channel with id ${reportSettings.reportChannel} was not found on the server/guild. Configure another channel.")
                }
            } else {
                LOG.warn("The guild with id ${reportSettings.guildId} was not found, maybe the bot was removed or maybe you shut down the bot responsible for this server.")
            }
        }
    }

    inner class WeeklyReportConfigurationSequence(
            user: User,
            channel: MessageChannel
    ) : Sequence(
            user,
            channel,
            true,
            true
    ) {
        private var sequenceNumber: Byte = 0

        init {
            channel.sendMessage("Please selection the action you want to perform:\n\n" +
                    "0. Set report channel\n" +
                    "1. add tracked member or role\n" +
                    "2. remove tracked member or role").queue { addMessageToCleaner(it) }
        }

        override fun onMessageReceivedDuringSequence(event: MessageReceivedEvent) {
            when (sequenceNumber) {
                0.toByte() -> {
                    when (event.message.contentRaw.toByte()) {
                        0.toByte() -> {
                            sequenceNumber = 1
                            channel.sendMessage("Please mention the channel or send the id of the channel").queue { addMessageToCleaner(it) }
                        }
                        1.toByte() -> {
                            sequenceNumber = 2
                            channel.sendMessage("Please mention the user or role or send the id of the role or user").queue { addMessageToCleaner(it) }
                        }
                        2.toByte() -> {
                            sequenceNumber = 3
                            channel.sendMessage("Please mention the user or role or send the id of the role or user").queue { addMessageToCleaner(it) }
                        }
                    }
                }
                1.toByte() -> {
                    val channelId = event.message.contentRaw.replace("<#", "").replace(">", "").toLong()
                    val guildId = event.guild.idLong
                    val activityReportSettings = activityReportSettingsRepository.findById(guildId).orElse(ActivityReportSettings(guildId))
                    activityReportSettings.reportChannel = channelId
                    activityReportSettingsRepository.save(activityReportSettings)
                    channel.sendMessage("Channel configured.").queue { it.delete().queueAfter(1, TimeUnit.MINUTES) }
                    destroy()
                }
                2.toByte() -> {
                    val roleOrMemberId = event.message.contentRaw.replace("<@", "").replace("&", "").replace(">", "").toLong()
                    val guildId = event.guild.idLong
                    val activityReportSettings = activityReportSettingsRepository.findById(guildId).orElse(ActivityReportSettings(guildId))
                    activityReportSettings.trackedRoleOrMember.add(roleOrMemberId)
                    activityReportSettingsRepository.save(activityReportSettings)
                    channel.sendMessage("Role or member added.").queue { it.delete().queueAfter(1, TimeUnit.MINUTES) }
                    destroy()
                }
                3.toByte() -> {
                    val roleOrMemberId = event.message.contentRaw.replace("<@", "").replace("&", "").replace(">", "").toLong()
                    val guildId = event.guild.idLong
                    val activityReportSettings = activityReportSettingsRepository.findById(guildId).orElse(ActivityReportSettings(guildId))
                    activityReportSettings.trackedRoleOrMember.remove(roleOrMemberId)
                    activityReportSettingsRepository.save(activityReportSettings)
                    channel.sendMessage("Role or member removed.").queue { it.delete().queueAfter(1, TimeUnit.MINUTES) }
                    destroy()
                }
            }
        }
    }
}