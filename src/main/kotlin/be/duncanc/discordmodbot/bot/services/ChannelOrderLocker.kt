package be.duncanc.discordmodbot.bot.services

import be.duncanc.discordmodbot.bot.commands.CommandModule
import be.duncanc.discordmodbot.data.entities.ChannelOrderLock
import be.duncanc.discordmodbot.data.repositories.ChannelOrderLockRepository
import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.entities.Category
import net.dv8tion.jda.api.events.channel.text.update.TextChannelUpdateParentEvent
import net.dv8tion.jda.api.events.channel.text.update.TextChannelUpdatePositionEvent
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.util.concurrent.TimeUnit.MINUTES

@Component
class ChannelOrderLocker(
        val channelOrderLockRepository: ChannelOrderLockRepository
) : CommandModule(
        arrayOf("unlockChannelOrder"),
        null,
        "Allows you to change the order of one channel per execution",
        requiredPermissions = *arrayOf(Permission.MANAGE_CHANNEL)
) {
    private val guildChannelPositionCache = HashMap<Long, HashMap<Long, Int>>()
    private val guildChannelParentCache = HashMap<Long, HashMap<Long, Category?>>()

    @Transactional
    override fun commandExec(event: MessageReceivedEvent, command: String, arguments: String?) {
        val guildId = event.guild.idLong
        val channelOrderLock = channelOrderLockRepository.findById(guildId)
                .orElse(ChannelOrderLock(guildId))
        if (channelOrderLock.enabled && channelOrderLock.locked) {
            guildChannelPositionCache[guildId] = HashMap()
            guildChannelParentCache[guildId] = HashMap()
            val orderLockUnlock = channelOrderLock.copy(unlocked = true)
            channelOrderLockRepository.save(orderLockUnlock)
            event.channel.sendMessage("${event.author.asMention} Any moderator can now change the order of channel for 2 minutes.").queue {
                it.delete().queueAfter(2, MINUTES) {
                    lockChannels(channelOrderLock)
                }
            }
        }
    }

    @Transactional
    override fun onTextChannelUpdatePosition(event: TextChannelUpdatePositionEvent) {
        val guildId = event.guild.idLong
        val channelOrderLock = channelOrderLockRepository.findById(guildId)
                .orElse(ChannelOrderLock(guildId))
        if (channelOrderLock.enabled && channelOrderLock.locked) {
            restoreOriginalChannelPosition(event)
        }
    }

    @Suppress("DuplicatedCode")
    private fun restoreOriginalChannelPosition(event: TextChannelUpdatePositionEvent) {
        val oldPosition = event.oldPosition
        val newPosition = event.newPosition
        val guildId = event.channel.idLong
        val channelId = event.channel.idLong
        val channelPositionCache = guildChannelPositionCache[guildId] ?: HashMap()
        val cachedPosition = channelPositionCache[channelId]
        if (cachedPosition != null && cachedPosition == newPosition) {
            return
        }
        if (cachedPosition == null) {
            channelPositionCache[channelId] = oldPosition
            guildChannelPositionCache[guildId] = channelPositionCache
        }
        event.channel.manager.setPosition(channelPositionCache[channelId]!!).queue()
    }

    @Transactional
    override fun onTextChannelUpdateParent(event: TextChannelUpdateParentEvent) {
        val guildId = event.guild.idLong
        val channelOrderLock = channelOrderLockRepository.findById(guildId)
                .orElse(ChannelOrderLock(guildId))
        if (channelOrderLock.enabled && channelOrderLock.locked) {
            restoreOriginalParent(event)
        }
    }

    @Suppress("DuplicatedCode")
    private fun restoreOriginalParent(event: TextChannelUpdateParentEvent) {
        val guildId = event.guild.idLong
        val oldParent = event.oldParent
        val newParent = event.newParent
        val channelId = event.channel.idLong
        val channelParentCache = guildChannelParentCache[guildId] ?: HashMap()
        val cachedCategory = channelParentCache[channelId]
        if (cachedCategory != null && cachedCategory == newParent) {
            return
        }
        channelParentCache[channelId] = oldParent
        guildChannelParentCache[guildId] = channelParentCache
        event.channel.manager.setParent(channelParentCache[channelId]).queue()
    }

    private fun lockChannels(channelOrderLock: ChannelOrderLock) {
        val chanelOrderLocking = channelOrderLock.copy(unlocked = false)
        channelOrderLockRepository.save(chanelOrderLocking)
    }
}
