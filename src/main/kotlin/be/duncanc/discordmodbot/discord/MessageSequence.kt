package be.duncanc.discordmodbot.discord


import net.dv8tion.jda.api.events.message.MessageReceivedEvent

interface MessageSequence {
    fun onMessageReceivedDuringSequence(event: MessageReceivedEvent)
}
