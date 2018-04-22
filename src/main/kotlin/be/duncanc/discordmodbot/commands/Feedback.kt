package be.duncanc.discordmodbot.commands

import be.duncanc.discordmodbot.data.repositories.ReportChannelRepository
import net.dv8tion.jda.core.entities.Guild
import net.dv8tion.jda.core.entities.TextChannel
import net.dv8tion.jda.core.events.message.MessageReceivedEvent
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

@Component
object Feedback : CommandModule(arrayOf("Feedback", "Report"), null, "This command allows users to give feedback to the server staff by posting it in a channel that is configured") {
    @Autowired
    private lateinit var reportChannelRepistory : ReportChannelRepository

    private val reportChannels = HashMap<Guild, TextChannel>()

    override fun commandExec(event: MessageReceivedEvent, command: String, arguments: String?) {
        TODO("not implemented")
    }
}