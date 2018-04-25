/*
 * MIT License
 *
 * Copyright (c) 2017 Duncan Casteleyn
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 *
 */

package be.duncanc.discordmodbot.bot.commands

import net.dv8tion.jda.core.entities.Guild
import net.dv8tion.jda.core.entities.Message
import net.dv8tion.jda.core.events.message.MessageReceivedEvent
import org.springframework.stereotype.Component

//todo make emotes not static
@Component
class ReactionVote private constructor() : CommandModule(arrayOf("ReactionVote", "Vote"), "[message id]", "Will put reactions to vote yes or no on a message.\nIf no message id is provided the message that contains the command will be used to vote.", cleanCommandMessage = false, ignoreWhiteList = true) {

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