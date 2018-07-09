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
import be.duncanc.discordmodbot.data.entities.GuildPointsSettings
import be.duncanc.discordmodbot.data.repositories.GuildPointsSettingsRepository
import net.dv8tion.jda.core.MessageBuilder
import net.dv8tion.jda.core.Permission
import net.dv8tion.jda.core.entities.MessageChannel
import net.dv8tion.jda.core.entities.TextChannel
import net.dv8tion.jda.core.entities.User
import net.dv8tion.jda.core.events.message.MessageReceivedEvent
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.util.concurrent.TimeUnit

@Component
class PointSettings(
        val guildPointsSettingsRepository: GuildPointsSettingsRepository
) : CommandModule(
        arrayOf("PointSettings"),
        null,
        "This command allows you to modify the settings for the point system.",
        requiredPermissions = *arrayOf(Permission.ADMINISTRATOR)
) {
    override fun commandExec(event: MessageReceivedEvent, command: String, arguments: String?) {
        event.jda.addEventListener(PointSettingsSequence(event.author, event.textChannel))
    }

    @Transactional
    inner class PointSettingsSequence(
            user: User,
            channel: MessageChannel
    ) : Sequence(
            user,
            channel
    ) {
        var sequenceNumber = 0.toByte()

        init {
            val guild = (channel as TextChannel).guild
            @Suppress("LeakingThis")
            val guildSettings = guildPointsSettingsRepository.findById(guild.idLong).orElse(GuildPointsSettings(guild.idLong))

            val announceChannelId = guildSettings.announceChannelId
            val announceChannel = if (announceChannelId != null) {
                guild.getTextChannelById(announceChannelId)?.name ?: "Chanel no longer exists"
            } else {
                "Not set"
            }

            val messageBuilder = MessageBuilder()
                    .append("What would you like to do?\n")
                    .append("\n0. Change max points per reason. Current value: ").append(guildSettings.maxPointsPerReason)
                    .append("\b1. Change the limit before a summary is announced with the users collected points. Current value: ").append(guildSettings.announcePointsSummaryLimit)
                    .append("\n2. Change the channel to announce the summary in. Current channel: ").append(announceChannel)
            channel.sendMessage(messageBuilder.build()).queue { super.addMessageToCleaner(it) }
        }

        override fun onMessageReceivedDuringSequence(event: MessageReceivedEvent) {
            when (sequenceNumber) {
                0.toByte() -> {
                    when (event.message.contentRaw.toByte()) {
                        0.toByte() -> {
                            channel.sendMessage("Please enter the new max amount of allowed points per reason.").queue { super.addMessageToCleaner(it) }
                            sequenceNumber = 1
                        }
                        1.toByte() -> {
                            channel.sendMessage("Please enter the new limit for a summary to be posted.").queue { super.addMessageToCleaner(it) }
                            sequenceNumber = 2
                        }
                        2.toByte() -> {
                            channel.sendMessage("Please mention the channel where you want announcement to be made. (These summaries use an everyone ping so do not use a public channel)").queue { super.addMessageToCleaner(it) }
                            sequenceNumber = 3
                        }
                        else -> {
                            throw IllegalArgumentException("You must select a number between 0 and 2")
                        }
                    }
                }
                1.toByte() -> {
                    val guild = (channel as TextChannel).guild
                    val guildSettings = guildPointsSettingsRepository.findById(guild.idLong).orElse(GuildPointsSettings(guild.idLong))
                    guildSettings.maxPointsPerReason = event.message.contentRaw.toInt()
                    guildPointsSettingsRepository.save(guildSettings)
                    channel.sendMessage("Successfully updated settings.").queue { it.delete().queueAfter(1, TimeUnit.MINUTES) }
                    super.destroy()
                }
                2.toByte() -> {
                    val guild = (channel as TextChannel).guild
                    val guildSettings = guildPointsSettingsRepository.findById(guild.idLong).orElse(GuildPointsSettings(guild.idLong))
                    guildSettings.announcePointsSummaryLimit = event.message.contentRaw.toInt()
                    guildPointsSettingsRepository.save(guildSettings)
                    channel.sendMessage("Successfully updated settings.").queue { it.delete().queueAfter(1, TimeUnit.MINUTES) }
                    super.destroy()
                }
                3.toByte() -> {
                    val guild = (channel as TextChannel).guild
                    val guildSettings = guildPointsSettingsRepository.findById(guild.idLong).orElse(GuildPointsSettings(guild.idLong))
                    guildSettings.announceChannelId = event.message.mentionedChannels[0].idLong
                    guildPointsSettingsRepository.save(guildSettings)
                    channel.sendMessage("Successfully updated settings.").queue { it.delete().queueAfter(1, TimeUnit.MINUTES) }
                    super.destroy()
                }
            }
        }
    }
}