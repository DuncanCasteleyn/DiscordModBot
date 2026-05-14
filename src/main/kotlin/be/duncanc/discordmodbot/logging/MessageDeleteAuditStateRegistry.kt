package be.duncanc.discordmodbot.logging

import be.duncanc.discordmodbot.logging.persistence.MessageDeleteAuditStateEntry
import be.duncanc.discordmodbot.logging.persistence.MessageDeleteAuditStateRepository
import org.springframework.stereotype.Component

@Component
class MessageDeleteAuditStateRegistry(
    private val messageDeleteAuditStateRepository: MessageDeleteAuditStateRepository
) {
    internal fun remember(guildId: Long, channelId: Long, targetUserId: Long, state: MessageDeleteAuditState) {
        if (state.consumedCounts.isEmpty()) {
            forget(guildId, channelId, targetUserId)
            return
        }

        messageDeleteAuditStateRepository.save(
            MessageDeleteAuditStateEntry(
                id = MessageDeleteAuditStateEntry.createId(guildId, channelId, targetUserId),
                guildId = guildId,
                channelId = channelId,
                targetUserId = targetUserId,
                consumedCounts = state.consumedCounts
            )
        )
    }

    internal fun get(guildId: Long, channelId: Long, targetUserId: Long): MessageDeleteAuditState? {
        return messageDeleteAuditStateRepository.findById(
            MessageDeleteAuditStateEntry.createId(guildId, channelId, targetUserId)
        ).orElse(null)?.toState()
    }

    internal fun forget(guildId: Long, channelId: Long, targetUserId: Long) {
        messageDeleteAuditStateRepository.deleteById(
            MessageDeleteAuditStateEntry.createId(guildId, channelId, targetUserId)
        )
    }

    private fun MessageDeleteAuditStateEntry.toState(): MessageDeleteAuditState {
        return MessageDeleteAuditState(consumedCounts = consumedCounts)
    }
}
