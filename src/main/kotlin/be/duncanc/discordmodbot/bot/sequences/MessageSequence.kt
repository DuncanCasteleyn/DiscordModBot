package be.duncanc.discordmodbot.bot.sequences

import net.dv8tion.jda.api.events.message.MessageReceivedEvent

interface MessageSequence {
    fun onMessageReceivedDuringSequence(event: MessageReceivedEvent)
}
