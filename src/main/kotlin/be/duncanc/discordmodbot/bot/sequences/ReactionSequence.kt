package be.duncanc.discordmodbot.bot.sequences

import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import net.dv8tion.jda.api.events.message.react.MessageReactionAddEvent

interface ReactionSequence {
    fun onReactionReceivedDuringSequence(event: MessageReactionAddEvent)
}
