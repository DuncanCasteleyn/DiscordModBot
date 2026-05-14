package be.duncanc.discordmodbot.logging

internal data class MessageDeleteAuditState(
    val entryId: Long,
    val count: Int,
    val consumedCount: Int
)

internal data class MessageDeleteAuditConsumeResult(
    val matched: Boolean,
    val nextState: MessageDeleteAuditState
)

internal object MessageDeleteAuditTracker {
    fun consume(
        previousState: MessageDeleteAuditState?,
        entryId: Long,
        count: Int
    ): MessageDeleteAuditConsumeResult {
        val consumedCount = if (previousState?.entryId == entryId) {
            previousState.consumedCount.coerceAtMost(count)
        } else {
            0
        }

        val matched = count > consumedCount
        return MessageDeleteAuditConsumeResult(
            matched,
            MessageDeleteAuditState(
                entryId = entryId,
                count = count,
                consumedCount = if (matched) consumedCount + 1 else consumedCount
            )
        )
    }
}
