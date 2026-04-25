package be.duncanc.discordmodbot.moderation

import net.dv8tion.jda.api.events.guild.GuildLeaveEvent
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import net.dv8tion.jda.api.hooks.ListenerAdapter
import org.springframework.stereotype.Component

@Component
class TrapChannelListener(
    private val trapChannelService: TrapChannelService
) : ListenerAdapter() {
    override fun onMessageReceived(event: MessageReceivedEvent) {
        trapChannelService.handleTrapMessage(event)
    }

    override fun onGuildLeave(event: GuildLeaveEvent) {
        trapChannelService.clearGuildState(event.guild.idLong)
    }
}
