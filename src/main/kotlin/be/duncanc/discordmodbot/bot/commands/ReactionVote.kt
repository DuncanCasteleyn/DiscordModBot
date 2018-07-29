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

import be.duncanc.discordmodbot.data.services.UserBlock
import net.dv8tion.jda.core.entities.Guild
import net.dv8tion.jda.core.entities.Message
import net.dv8tion.jda.core.events.message.MessageReceivedEvent
import org.springframework.stereotype.Component

//todo make emotes not static
@Component
class ReactionVote(
        userBlock: UserBlock
) : CommandModule(
        arrayOf("ReactionVote", "Vote"),
        "[message id]",
        "Will put reactions to vote yes or no on a message.\nIf no message id is provided the message that contains the command will be used to vote.",
        cleanCommandMessage = false,
        ignoreWhitelist = true,
        userBlock = userBlock
) {

    companion object {
        private const val EMOTE_SOURCE = 160450060436504578L
    }

    override fun commandExec(event: MessageReceivedEvent, command: String, arguments: String?) {
        val emoteSource: Guild = event.jda.getGuildById(EMOTE_SOURCE)
        try {
            val messageId = arguments!!.toLong()
            event.textChannel.getMessageById(messageId).queue {
                addVoteReactions(it, emoteSource)
            }
            event.message.delete().queue()
        } catch (nfe: NumberFormatException) {
            useReceivedMessage(event, emoteSource)
        } catch (npe: NullPointerException) {
            useReceivedMessage(event, emoteSource)
        }
    }

    private fun useReceivedMessage(event: MessageReceivedEvent, emoteSource: Guild) {
        addVoteReactions(event.message, emoteSource)
    }

    private fun addVoteReactions(it: Message, emoteSource: Guild) {
        it.addReaction(emoteSource.getEmotesByName("voteYes", false)[0]).queue()
        it.addReaction(emoteSource.getEmotesByName("voteNo", false)[0]).queue()
    }
}