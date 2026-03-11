package be.duncanc.discordmodbot.discord


import net.dv8tion.jda.api.events.message.react.MessageReactionAddEvent

interface ReactionSequence {
    fun onReactionReceivedDuringSequence(event: MessageReactionAddEvent)
}
