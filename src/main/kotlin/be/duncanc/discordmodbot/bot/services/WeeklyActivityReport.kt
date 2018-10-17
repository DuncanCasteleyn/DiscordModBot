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
import be.duncanc.discordmodbot.bot.utils.JDALibHelper
import be.duncanc.discordmodbot.data.repositories.ActivityReportSettingsRepository
import net.dv8tion.jda.core.JDA
import net.dv8tion.jda.core.MessageBuilder
import net.dv8tion.jda.core.entities.Member
import net.dv8tion.jda.core.entities.TextChannel
import net.dv8tion.jda.core.events.ReadyEvent
import net.dv8tion.jda.core.events.ShutdownEvent
import net.dv8tion.jda.core.events.message.MessageReceivedEvent
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Duration
import java.time.OffsetDateTime

@Component
class WeeklyActivityReport(
        private val activityReportSettingsRepository: ActivityReportSettingsRepository
) : CommandModule(
        arrayOf("WeeklyActivitySettings"),
        null,
        "Allows you to configure weekly reports"
) {
    companion object {
        val LOG = LoggerFactory.getLogger(WeeklyActivityReport::class.java)
    }

    val instances = HashSet<JDA>()

    override fun onReady(event: ReadyEvent) {
        instances.add(event.jda)
    }

    override fun onShutdown(event: ShutdownEvent) {
        instances.remove(event.jda)
    }

    override fun commandExec(event: MessageReceivedEvent, command: String, arguments: String?) {
        TODO("not implemented")
    }

    @Scheduled(cron = "0 0 * * 1")
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
                    val stats = HashMap<TextChannel, HashMap<Member, Long>>()
                    guild.textChannels.forEach {
                        val channelStats = HashMap<Member, Long>()
                        it.iterableHistory.forEach history@{ message ->
                            if (trackedMembers.contains(message.member)) {
                                channelStats[message.member] = (channelStats[message.member] ?: 0L) + 1L
                            }
                            if (Duration.between(message.creationTime, statsCollectionStartTime).toDays() >= 7) {
                                return@history
                            }
                        }
                        stats[it] = channelStats
                    }
                    val message = MessageBuilder()
                    message.append("**Statistics of the past 7 days**\n")
                    stats.forEach { channel, channelStats ->
                        message.append("\n#${channel.name}\n\n")
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
}