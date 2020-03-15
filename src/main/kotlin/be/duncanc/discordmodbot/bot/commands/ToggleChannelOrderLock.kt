package be.duncanc.discordmodbot.bot.commands

import be.duncanc.discordmodbot.data.entities.ChannelOrderLock
import be.duncanc.discordmodbot.data.repositories.ChannelOrderLockRepository
import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.util.concurrent.TimeUnit

@Component
class ToggleChannelOrderLock(
        val channelOrderLockRepository: ChannelOrderLockRepository
) : CommandModule(
        arrayOf("ToggleChannelOrderLock"),
        null,
        "Enables/disables channel order locking for the guild",
        requiredPermissions = *arrayOf(Permission.MANAGE_CHANNEL)
) {
    @Transactional
    override fun commandExec(event: MessageReceivedEvent, command: String, arguments: String?) {
        val guildId = event.guild.idLong
        val channelOrderLock = channelOrderLockRepository.findById(guildId)
                .orElse(ChannelOrderLock(guildId))
        val reverseChannelOrderLock = channelOrderLock.copy(enabled = !channelOrderLock.enabled)
        channelOrderLockRepository.save(reverseChannelOrderLock)
        val lockingStatusText = if (reverseChannelOrderLock.enabled) "enabled" else "disabled"
        event.channel.sendMessage("Channel order locking is now $lockingStatusText.").queue { it.delete().queueAfter(1, TimeUnit.MINUTES) }
    }
}
