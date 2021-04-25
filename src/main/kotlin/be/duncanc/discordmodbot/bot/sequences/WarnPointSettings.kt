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
import be.duncanc.discordmodbot.data.entities.GuildWarnPointsSettings
import be.duncanc.discordmodbot.data.repositories.jpa.GuildWarnPointsSettingsRepository
import net.dv8tion.jda.api.MessageBuilder
import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.MessageChannel
import net.dv8tion.jda.api.entities.TextChannel
import net.dv8tion.jda.api.entities.User
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.util.concurrent.TimeUnit

@Component
class WarnPointSettings(
    val guildWarnPointsSettingsRepository: GuildWarnPointsSettingsRepository
) : CommandModule(
    arrayOf("WarnPointSettings"),
    null,
    "This command allows you to modify the settings for the point system.",
    requiredPermissions = arrayOf(Permission.ADMINISTRATOR)
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
    ), MessageSequence {
        var sequenceNumber = 0.toByte()

        init {
            val guild = (channel as TextChannel).guild

            @Suppress("LeakingThis")
            val guildSettings = guildWarnPointsSettings(guild)

            val announceChannelId = guildSettings.announceChannelId
            val announceChannel = guild.getTextChannelById(announceChannelId)?.name ?: "Chanel no longer exists"

            val messageBuilder = MessageBuilder()
                .append("What would you like to do?\n")
                .append("\n0. Change max points per reason. Current value: ").append(guildSettings.maxPointsPerReason)
                .append("\n1. Change the limit before a summary is announced with the users collected points. Current value: ")
                .append(guildSettings.announcePointsSummaryLimit)
                .append("\n2. Change the channel to announce the summary in. Current channel: ").append(announceChannel)
                .append("\n3. Toggle Warn command override by replacing Warn with AddPoints command. Current value: ")
                .append(guildSettings.overrideWarnCommand)
            channel.sendMessage(messageBuilder.build()).queue { super.addMessageToCleaner(it) }
        }

        override fun onMessageReceivedDuringSequence(event: MessageReceivedEvent) {
            when (sequenceNumber) {
                0.toByte() -> {
                    when (event.message.contentRaw.toByte()) {
                        0.toByte() -> {
                            channel.sendMessage("Please enter the new max amount of allowed points per reason.")
                                .queue { super.addMessageToCleaner(it) }
                            sequenceNumber = 1
                        }
                        1.toByte() -> {
                            channel.sendMessage("Please enter the new limit for a summary to be posted.")
                                .queue { super.addMessageToCleaner(it) }
                            sequenceNumber = 2
                        }
                        2.toByte() -> {
                            channel.sendMessage("Please mention the channel where you want announcement to be made. (These summaries use an everyone ping so don't use a public channel)")
                                .queue { super.addMessageToCleaner(it) }
                            sequenceNumber = 3
                        }
                        3.toByte() -> {
                            val guildSettings = guildWarnPointsSettings(event.guild)
                            guildSettings.overrideWarnCommand = !guildSettings.overrideWarnCommand
                            guildWarnPointsSettingsRepository.save(guildSettings)
                            saveSuccessMessage()
                            super.destroy()
                        }
                        else -> {
                            throw IllegalArgumentException("You must select a number between 0 and 2")
                        }
                    }
                }
                1.toByte() -> {
                    val guild = (channel as TextChannel).guild
                    val guildSettings = guildWarnPointsSettings(guild)
                    guildSettings.maxPointsPerReason = event.message.contentRaw.toInt()
                    guildWarnPointsSettingsRepository.save(guildSettings)
                    saveSuccessMessage()
                    super.destroy()
                }
                2.toByte() -> {
                    val guild = (channel as TextChannel).guild
                    val guildSettings = guildWarnPointsSettings(guild)
                    guildSettings.announcePointsSummaryLimit = event.message.contentRaw.toInt()
                    guildWarnPointsSettingsRepository.save(guildSettings)
                    saveSuccessMessage()
                    super.destroy()
                }
                3.toByte() -> {
                    val guild = (channel as TextChannel).guild
                    val guildSettings = guildWarnPointsSettings(guild)
                    guildSettings.announceChannelId = event.message.mentionedChannels[0].idLong
                    guildWarnPointsSettingsRepository.save(guildSettings)
                    saveSuccessMessage()
                    super.destroy()
                }
            }
        }

        private fun saveSuccessMessage() {
            channel.sendMessage("Successfully updated settings.").queue { it.delete().queueAfter(1, TimeUnit.MINUTES) }
        }
    }

    private fun guildWarnPointsSettings(guild: Guild) =
        guildWarnPointsSettingsRepository.findById(guild.idLong)
            .orElse(GuildWarnPointsSettings(guild.idLong, announceChannelId = -1))
}
