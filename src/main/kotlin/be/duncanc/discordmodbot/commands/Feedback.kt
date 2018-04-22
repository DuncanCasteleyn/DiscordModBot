package be.duncanc.discordmodbot.commands

import net.dv8tion.jda.core.entities.Guild
import net.dv8tion.jda.core.entities.TextChannel
import net.dv8tion.jda.core.events.message.MessageReceivedEvent

object Feedback : CommandModule(arrayOf("Feedback", "Report"), null, "This command allows users to give feedback to the server staff by posting it in a channel that is configured") {
    private val reportChannels = HashMap<Guild, TextChannel>()

    override fun commandExec(event: MessageReceivedEvent, command: String, arguments: String?) {
        TODO("not implemented")
    }
}