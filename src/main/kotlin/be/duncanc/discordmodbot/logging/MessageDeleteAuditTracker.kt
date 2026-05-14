package be.duncanc.discordmodbot.logging

internal data class MessageDeleteAuditKey(
    val guildId: Long,
    val channelId: Long,
    val targetUserId: Long
)

internal data class MessageDeleteAuditCandidate(
    val entryId: Long,
    val count: Int
)

internal data class MessageDeleteAuditState(
    val consumedCounts: Map<Long, Int>
)

internal data class MessageDeleteAuditConsumeResult(
    val matchedEntryId: Long?,
    val nextState: MessageDeleteAuditState
)

internal object MessageDeleteAuditTracker {
    fun consume(
        previousState: MessageDeleteAuditState?,
        candidates: List<MessageDeleteAuditCandidate>
    ): MessageDeleteAuditConsumeResult {
        val visibleEntryIds = candidates.asSequence().map { it.entryId }.toSet()
        val nextConsumedCounts = previousState?.consumedCounts
            ?.filterKeys { it in visibleEntryIds }
            ?.toMutableMap()
            ?: mutableMapOf()

        for (candidate in candidates.asReversed()) {
            val consumedCount = nextConsumedCounts[candidate.entryId] ?: 0
            if (candidate.count > consumedCount) {
                nextConsumedCounts[candidate.entryId] = consumedCount + 1
                return MessageDeleteAuditConsumeResult(
                    matchedEntryId = candidate.entryId,
                    nextState = MessageDeleteAuditState(nextConsumedCounts.toMap())
                )
            }
        }

        return MessageDeleteAuditConsumeResult(
            matchedEntryId = null,
            nextState = MessageDeleteAuditState(nextConsumedCounts.toMap())
        )
    }
}
