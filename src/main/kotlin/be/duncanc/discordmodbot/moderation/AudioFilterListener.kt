package be.duncanc.discordmodbot.moderation

import net.dv8tion.jda.api.events.guild.GuildLeaveEvent
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import net.dv8tion.jda.api.hooks.ListenerAdapter
import org.springframework.stereotype.Component

@Component
class AudioFilterListener(
    private val audioFilterService: AudioFilterService
) : ListenerAdapter() {
    override fun onMessageReceived(event: MessageReceivedEvent) {
        audioFilterService.handleMessage(event)
    }

    override fun onGuildLeave(event: GuildLeaveEvent) {
        audioFilterService.clearGuildState(event.guild.idLong)
    }
}
