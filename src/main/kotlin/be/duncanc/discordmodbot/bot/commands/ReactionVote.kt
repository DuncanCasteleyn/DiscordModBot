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

package be.duncanc.discordmodbot.bot.commands

import be.duncanc.discordmodbot.data.repositories.VotingEmotesRepository
import be.duncanc.discordmodbot.data.services.UserBlockService
import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import org.springframework.stereotype.Component

@Component
class ReactionVote(
        private val votingEmotesRepository: VotingEmotesRepository,
        userBlockService: UserBlockService
) : CommandModule(
        arrayOf("ReactionVote", "Vote"),
        "[message id] [Vote number count]",
        "Will put reactions to vote yes or no on a message.\nIf no message id is provided the message that contains the command will be used to vote.\nif a number is provided after the id a numeric vote is started with x amount of voting options with a max of 11",
        cleanCommandMessage = false,
        ignoreWhitelist = true,
        userBlockService = userBlockService
) {
    companion object {
        val numericVoteEmotes = arrayOf("0⃣", "1⃣", "2⃣", "3⃣", "4⃣", "5⃣", "6⃣", "7⃣", "8⃣", "9⃣", "\uD83D\uDD1F")
    }

    override fun commandExec(event: MessageReceivedEvent, command: String, arguments: String?) {
        if (arguments != null) {
            val split = arguments.split(" ", limit = 2)
            try {
                val messageId = split[0].toLong()
                event.textChannel.retrieveMessageById(messageId).queue {
                    if (split.size == 1) {
                        it.addYesOrNoVoteReactions()
                    } else {
                        it.addNumericVoteReactions(split[1].toByte())
                    }
                }
                event.message.delete().queue()
            } catch (nfe: NumberFormatException) {
                event.message.addYesOrNoVoteReactions()
            }
        } else {
            event.message.addYesOrNoVoteReactions()
        }
    }

    private fun Message.addYesOrNoVoteReactions() {
        votingEmotesRepository.findById(guild.idLong).ifPresentOrElse({
            addReaction(jda.getEmoteById(it.voteYesEmote!!)!!).queue()
            addReaction(jda.getEmoteById(it.voteNoEmote!!)!!).queue()
        }, {
            addReaction("✅").queue()
            addReaction("❎").queue()
        })
    }

    private fun Message.addNumericVoteReactions(maxReactions: Byte) {
        require(maxReactions <= 11) { "A value above 11 is not supported" }
        require(maxReactions > 1) { "A value below 1 is not supported" }

        for (x in 0 until maxReactions) {
            addReaction(numericVoteEmotes[x]).queue()
        }
    }
}
