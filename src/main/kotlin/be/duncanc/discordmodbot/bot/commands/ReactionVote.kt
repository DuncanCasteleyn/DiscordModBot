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

import be.duncanc.discordmodbot.data.entities.VoteEmotes
import be.duncanc.discordmodbot.data.repositories.VotingEmotesRepository
import be.duncanc.discordmodbot.data.services.UserBlockService
import net.dv8tion.jda.core.entities.Message
import net.dv8tion.jda.core.events.message.MessageReceivedEvent
import org.springframework.stereotype.Component

@Component
class ReactionVote(
    private val votingEmotesRepository: VotingEmotesRepository,
    userBlockService: UserBlockService
) : CommandModule(
    arrayOf("ReactionVote", "Vote"),
    "[message id]",
    "Will put reactions to vote yes or no on a message.\nIf no message id is provided the message that contains the command will be used to vote.",
    cleanCommandMessage = false,
    ignoreWhitelist = true,
    userBlockService = userBlockService
) {

    override fun commandExec(event: MessageReceivedEvent, command: String, arguments: String?) {
        try {
            val messageId = arguments!!.toLong()
            event.textChannel.getMessageById(messageId).queue {
                it.addVoteReactions()
            }
            event.message.delete().queue()
        } catch (nfe: NumberFormatException) {
            event.message.addVoteReactions()
        } catch (npe: NullPointerException) {
            event.message.addVoteReactions()
        }
    }

    private fun Message.addVoteReactions() {
        val voteEmotes: VoteEmotes? = votingEmotesRepository.findById(guild.idLong)
            .orElse(null)
        if (voteEmotes != null) {
            addReaction(jda.getEmoteById(voteEmotes.voteYesEmote!!)).queue()
            addReaction(jda.getEmoteById(voteEmotes.voteNoEmote!!)).queue()
        } else {
            addReaction("✅").queue()
            addReaction("❎").queue()
        }
    }
}