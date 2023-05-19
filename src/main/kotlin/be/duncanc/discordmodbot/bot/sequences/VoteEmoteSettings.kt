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
import be.duncanc.discordmodbot.data.entities.VoteEmotes
import be.duncanc.discordmodbot.data.repositories.jpa.VotingEmotesRepository
import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.entities.User
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.util.concurrent.TimeUnit

@Component
class VoteEmoteSettings(
    val votingEmotesRepository: VotingEmotesRepository
) : CommandModule(
    arrayOf("VoteEmoteSettings", "VoteSettings"),
    null,
    "Command to change the vote yes and no emotes for a server",
    requiredPermissions = arrayOf(Permission.MANAGE_EMOJIS_AND_STICKERS)
) {
    override fun commandExec(event: MessageReceivedEvent, command: String, arguments: String?) {
        event.jda.addEventListener(VoteEmoteSettingsSequence(votingEmotesRepository, event.author, event.channel))
    }
}

open class VoteEmoteSettingsSequence(
    private val votingEmotesRepository: VotingEmotesRepository,
    user: User,
    channel: MessageChannel
) : Sequence(
    user,
    channel,
    true,
    false
), MessageSequence {
    init {
        channel.sendMessage("${user.asMention} Please send the emote you want to use for yes votes.\nMake sure the bot has access to the server where this emote is hosted.")
            .queue { addMessageToCleaner(it) }
    }

    private var voteYesEmoteId: Long? = null

    @Transactional
    override fun onMessageReceivedDuringSequence(event: MessageReceivedEvent) {
        when (voteYesEmoteId) {
            null -> {
                val voteNotEmote = event.message.reactions[0].emoji.asCustom()
                voteYesEmoteId = voteNotEmote.idLong
                channel.sendMessage("Please send the emote you want to use for no votes")
                    .queue { addMessageToCleaner(it) }
            }
            else -> {
                val voteNoEmote = event.message.reactions[0].emoji.asCustom()
                votingEmotesRepository.save(VoteEmotes(event.guild.idLong, voteYesEmoteId!!, voteNoEmote.idLong))
                channel.sendMessage("New emotes have been set")
                    .queue { it.delete().queueAfter(1, TimeUnit.MINUTES) }
                destroy()
            }
        }
    }
}
