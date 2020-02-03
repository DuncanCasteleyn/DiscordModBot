package be.duncanc.discordmodbot.bot.sequences

import be.duncanc.discordmodbot.bot.commands.CommandModule
import net.dv8tion.jda.api.entities.Category
import net.dv8tion.jda.api.events.channel.text.update.TextChannelUpdateParentEvent
import net.dv8tion.jda.api.events.channel.text.update.TextChannelUpdatePositionEvent
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import org.springframework.stereotype.Component
import java.util.concurrent.TimeUnit.MINUTES

@Component
class ChannelOrderLocker : CommandModule(
        arrayOf("unlockChannelOrder"),
        null,
        "Allows you to change the order of one channel per execution"
) {
    private var channelsLocked = true
    private val channelPositionCache = HashMap<Long, Int>()
    private val channelParentCache = HashMap<Long, Category?>()

    override fun commandExec(event: MessageReceivedEvent, command: String, arguments: String?) {
        channelPositionCache.clear()
        channelParentCache.clear()
        channelsLocked = false
        event.channel.sendMessage("${event.author.asMention} Any moderator can now change the order of one channel.").queue {
            it.delete().queueAfter(1, MINUTES)
        }
    }

    override fun onTextChannelUpdatePosition(event: TextChannelUpdatePositionEvent) {
        if (channelsLocked && event.guild.idLong == 175856762677624832) {
            val oldPosition = event.oldPosition
            val newPosition = event.newPosition
            val channelId = event.channel.idLong
            val cachedPosition = channelPositionCache[channelId]
            if (cachedPosition != null && cachedPosition == newPosition) {
                return
            }
            if (cachedPosition == null) {
                channelPositionCache[channelId] = oldPosition
            }
            event.channel.manager.setPosition(channelPositionCache[channelId]!!).queue()
        } else {
            channelsLocked = true
        }
    }

    override fun onTextChannelUpdateParent(event: TextChannelUpdateParentEvent) {
        if (channelsLocked && event.guild.idLong == 175856762677624832) {
            val oldParent = event.oldParent
            val newParent = event.newParent
            val channelId = event.channel.idLong
            val cachedCategory = channelParentCache[channelId]
            val cachedParent = cachedCategory
            if (cachedParent != null && cachedParent == newParent) {
                return
            }
            channelParentCache[channelId] = oldParent
            event.channel.manager.setParent(channelParentCache[channelId]).queue()
        } else {
            channelsLocked = true
        }
    }
}
