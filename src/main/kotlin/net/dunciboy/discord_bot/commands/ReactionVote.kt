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

package net.dunciboy.discord_bot.commands

import net.dv8tion.jda.core.entities.Guild
import net.dv8tion.jda.core.entities.Message
import net.dv8tion.jda.core.events.message.MessageReceivedEvent

class ReactionVote : CommandModule(ALIASES, DESCRIPTION, ARGUMENTATION, cleanCommandMessage = false) {

    companion object {
        private val ALIASES = arrayOf("ReactionVote", "Vote")
        private const val DESCRIPTION = "Will put reactions to vote yes or no something on a message"
        private const val ARGUMENTATION = "message id"
        private const val EMOTE_SOURCE = 160450060436504578L
    }

    override fun commandExec(event: MessageReceivedEvent, command: String, arguments: String?) {
        val emoteSource: Guild = event.jda.getGuildById(EMOTE_SOURCE)
        if (arguments == null) {
            val emoteSource: Guild = event.jda.getGuildById(EMOTE_SOURCE)
            addVoteReactions(event.message, emoteSource)
        } else {
            event.textChannel.getMessageById(arguments).queue {
                addVoteReactions(it, emoteSource)
            }
            event.message.delete().queue()
        }
    }

    private fun addVoteReactions(it: Message, emoteSource: Guild) {
        it.addReaction(emoteSource.getEmotesByName("voteYes", false)[0]).queue()
        it.addReaction(emoteSource.getEmotesByName("voteNo", false)[0]).queue()
    }
}