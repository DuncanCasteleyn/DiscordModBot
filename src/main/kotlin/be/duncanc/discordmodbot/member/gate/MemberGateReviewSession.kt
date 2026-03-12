package be.duncanc.discordmodbot.member.gate

class MemberGateReviewSession(
    pendingUserIds: List<Long>,
    val oldestPendingUserId: Long = pendingUserIds.firstOrNull()
        ?: throw IllegalArgumentException("A review session requires at least one pending user.")
) {
    private val queuedUserIds = ArrayDeque(pendingUserIds)

    private var currentUserId: Long? = queuedUserIds.removeFirstOrNull()

    fun getCurrentUserId(): Long? = currentUserId

    fun isCurrentOldest(): Boolean = currentUserId == oldestPendingUserId

    fun toPendingUserIds(): List<Long> = listOfNotNull(currentUserId) + queuedUserIds.toList()

    fun skipCurrent(): Long? {
        val currentUserId = currentUserId ?: return null
        queuedUserIds.addLast(currentUserId)
        this.currentUserId = queuedUserIds.removeFirstOrNull()
        return this.currentUserId
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
