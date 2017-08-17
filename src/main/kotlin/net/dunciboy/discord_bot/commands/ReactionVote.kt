package net.dunciboy.discord_bot.commands

import net.dv8tion.jda.core.entities.Guild
import net.dv8tion.jda.core.events.message.MessageReceivedEvent

class ReactionVote : CommandModule(ALIASES, DESCRIPTION, ARGUMENTATION) {

    companion object {
        private val ALIASES = arrayOf("ReactionVote", "Vote")
        private const val DESCRIPTION = "Will put reactions to vote yes or no something on a message"
        private const val ARGUMENTATION = "message id"
        private const val EMOTE_SOURCE = 160450060436504578L
    }

    override fun commandExec(event: MessageReceivedEvent, command: String, arguments: String?) {
        if (arguments == null) {
            throw IllegalArgumentException("Argument can't be null.")
        }

        event.textChannel.getMessageById(arguments).queue {
            val emoteSource: Guild = event.jda.getGuildById(EMOTE_SOURCE)
            it.addReaction(emoteSource.getEmotesByName("voteYes", false)[0]).queue()
            it.addReaction(emoteSource.getEmotesByName("voteNo", false)[0]).queue()
        }
    }
}