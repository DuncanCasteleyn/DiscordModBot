package be.duncanc.discordmodbot.member.gate

import java.util.UUID

class ReviewSession(
    pendingUserIds: List<Long>,
    val sessionId: String = UUID.randomUUID().toString(),
    val oldestPendingUserId: Long = pendingUserIds.firstOrNull()
        ?: throw IllegalArgumentException("A review session requires at least one pending user."),
    var approvedCount: Int = 0,
    var rejectedCount: Int = 0,
    var manualActionCount: Int = 0
) {
    init {
        if (pendingUserIds.isEmpty()) {
            throw IllegalArgumentException("A review session requires at least one pending user.")
        }
    }

    private val queuedUserIds = ArrayDeque(pendingUserIds)

    private var currentUserId: Long? = queuedUserIds.removeFirstOrNull()

    fun getCurrentUserId(): Long? = currentUserId

    fun isCurrentOldest(): Boolean = currentUserId == oldestPendingUserId

    fun toPendingUserIds(): List<Long> = listOfNotNull(currentUserId) + queuedUserIds.toList()

    fun recordApproval() {
        approvedCount++
    }

    fun recordRejection() {
        rejectedCount++
    }

    fun recordManualAction() {
        manualActionCount++
    }

    fun advanceAfterReview(): Long? {
        currentUserId = queuedUserIds.removeFirstOrNull()
        return currentUserId
    }

    fun advancePastResolvedCurrent(): Long? {
        currentUserId = queuedUserIds.removeFirstOrNull()
        return currentUserId
    }

    fun complete() {
        currentUserId = null
        queuedUserIds.clear()
    }
}
