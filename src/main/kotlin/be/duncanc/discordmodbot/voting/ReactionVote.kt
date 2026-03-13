package be.duncanc.discordmodbot.voting

import be.duncanc.discordmodbot.discord.CommandModule
import be.duncanc.discordmodbot.discord.UserBlockService
import be.duncanc.discordmodbot.voting.persistence.VotingEmotesRepository
import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.entities.emoji.Emoji
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
        val numericVoteEmotes = arrayOf("1⃣", "2⃣", "3⃣", "4⃣", "5⃣", "6⃣", "7⃣", "8⃣", "9⃣", "\uD83D\uDD1F", "0⃣")
    }

    override fun commandExec(event: MessageReceivedEvent, command: String, arguments: String?) {
        if (arguments != null) {
            val split = arguments.split(" ", limit = 2)
            try {
                val messageId = split[0].toLong()
                event.guildChannel.retrieveMessageById(messageId).queue {
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
            addReaction(jda.getEmojiById(it.voteYesEmote)!!).queue()
            addReaction(jda.getEmojiById(it.voteNoEmote)!!).queue()
        }, {
            addReaction(Emoji.fromUnicode("✅")).queue()
            addReaction(Emoji.fromUnicode("❎")).queue()
        })
    }

    private fun Message.addNumericVoteReactions(maxReactions: Byte) {
        require(maxReactions <= 11) { "A value above 11 is not supported" }
        require(maxReactions > 1) { "A value below 1 is not supported" }

        for (x in 0 until maxReactions) {
            addReaction(Emoji.fromUnicode(numericVoteEmotes[x])).queue()
        }
    }
}
