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
import be.duncanc.discordmodbot.bot.utils.nicknameAndUsername
import be.duncanc.discordmodbot.data.entities.ReportChannel
import be.duncanc.discordmodbot.data.repositories.ReportChannelRepository
import be.duncanc.discordmodbot.data.services.UserBlockService
import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.MessageBuilder
import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.entities.ChannelType
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.MessageChannel
import net.dv8tion.jda.api.entities.User
import net.dv8tion.jda.api.events.ReadyEvent
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import java.awt.Color
import java.time.OffsetDateTime
import java.util.concurrent.TimeUnit

@Component
class Feedback
@Autowired constructor(
    private val reportChannelRepository: ReportChannelRepository,
    userBlockService: UserBlockService
) : CommandModule(
    arrayOf("Feedback", "Report", "Complaint"),
    null,
    "This command allows users to give feedback to the server staff by posting it in a channel that is configured. This command must be executed in a private channel",
    userBlockService = userBlockService
) {

    private val setFeedbackChannel = SetFeedbackChannel()
    private val disableFeedback = DisableFeedback()

    override fun commandExec(event: MessageReceivedEvent, command: String, arguments: String?) {
        if (!event.isFromType(ChannelType.PRIVATE)) {
            throw UnsupportedOperationException("Feedback must be provided in private chat.")
        }
        event.jda.addEventListener(FeedbackSequence(event.author, event.privateChannel, event.jda))
    }

    override fun onReady(event: ReadyEvent) {
        event.jda.addEventListener(setFeedbackChannel, disableFeedback)
    }

    inner class FeedbackSequence(user: User, channel: MessageChannel, jda: JDA) :
        Sequence(user, channel, cleanAfterSequence = false, informUser = true) {
        private var guild: Guild? = null
        private val selectableGuilds: List<Guild>

        init {
            val validGuilds = reportChannelRepository.findAll()
            selectableGuilds =
                    jda.guilds.filter { guild -> validGuilds.any { it.guildId == guild.idLong } && guild.isMember(user) }
                        .toList()
            if (selectableGuilds.isEmpty()) {
                throw UnsupportedOperationException("None of the servers you are on have this feature enabled.")
            }

            val messageBuilder = MessageBuilder()
            messageBuilder.append("Please select which guild your like to report feedback to:\n\n")
            for (i in selectableGuilds.indices) {
                messageBuilder.append(i).append(". ").append(selectableGuilds[i].name).append('\n')
            }
            messageBuilder.buildAll(MessageBuilder.SplitPolicy.NEWLINE).forEach {
                channel.sendMessage(it).queue()
            }
        }

        override fun onMessageReceivedDuringSequence(event: MessageReceivedEvent) {
            if (guild == null) {
                guild = selectableGuilds[event.message.contentRaw.toInt()]
                channel.sendMessage("Please enter your feedback.").queue()
            } else {
                val feedbackChannel =
                        reportChannelRepository.findById(guild!!.idLong).orElseThrow { throw RuntimeException("The feedback feature was disabled during runtime") }.textChannelId
                val embedBuilder = EmbedBuilder()
                        .setAuthor(guild!!.getMember(user)!!.nicknameAndUsername, null, user.effectiveAvatarUrl)
                    .setDescription(event.message.contentStripped)
                    .setFooter(user.id, null)
                    .setTimestamp(OffsetDateTime.now())
                    .setColor(Color.GREEN)
                guild!!.getTextChannelById(feedbackChannel)!!.sendMessage(embedBuilder.build()).queue()
                channel.sendMessage("Your feedback has been transferred to the moderators.\n\nThank you for helping us.")
                    .queue()
                super.destroy()
            }
        }
    }

    inner class SetFeedbackChannel : CommandModule(
        arrayOf("SetFeedbackChannel"),
        null,
        "This command sets the current channel as feedback channel enabling the !feedback command for the server.",
        ignoreWhitelist = true,
        requiredPermissions = *arrayOf(Permission.MANAGE_CHANNEL)
    ) {
        override fun commandExec(event: MessageReceivedEvent, command: String, arguments: String?) {
            reportChannelRepository.save(ReportChannel(event.guild.idLong, event.textChannel.idLong))
            event.channel.sendMessage("This channel has been set for feedback.")
                .queue { it.delete().queueAfter(1, TimeUnit.MINUTES) }
        }
    }

    inner class DisableFeedback : CommandModule(
        arrayOf("DisableFeedback"),
        null,
        "This command disables the feedback system for the server where executed.",
        ignoreWhitelist = true,
        requiredPermissions = *arrayOf(Permission.MANAGE_CHANNEL)
    ) {
        override fun commandExec(event: MessageReceivedEvent, command: String, arguments: String?) {
            reportChannelRepository.deleteById(event.guild.idLong)
            event.channel.sendMessage("Disabled feedback.").queue { it.delete().queueAfter(1, TimeUnit.MINUTES) }
        }
    }
}
