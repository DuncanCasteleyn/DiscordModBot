package be.duncanc.discordmodbot.member.gate

import be.duncanc.discordmodbot.member.gate.persistence.ReviewSessionState
import be.duncanc.discordmodbot.member.gate.persistence.ReviewSessionStateRepository
import org.springframework.stereotype.Component

@Component
class ReviewSessionRegistry(
    private val reviewSessionStateRepository: ReviewSessionStateRepository
) {
    data class StoredReviewSession(
        val reviewerId: Long,
        val session: ReviewSession
    )

    fun remember(guildId: Long, reviewerId: Long, session: ReviewSession) {
        val pendingUserIds = session.toPendingUserIds()
        if (pendingUserIds.isEmpty()) {
            forget(guildId, reviewerId)
            return
        }

        reviewSessionStateRepository.save(
            ReviewSessionState(
                id = createId(guildId, reviewerId),
                guildId = guildId,
                reviewerId = reviewerId,
                oldestPendingUserId = session.oldestPendingUserId,
                pendingUserIds = pendingUserIds,
                approvedCount = session.approvedCount,
                rejectedCount = session.rejectedCount,
                manualActionCount = session.manualActionCount
            )
        )
    }

    fun get(guildId: Long, reviewerId: Long): ReviewSession? {
        val id = createId(guildId, reviewerId)
        val state = reviewSessionStateRepository.findById(id).orElse(null) ?: return null
        if (state.pendingUserIds.isEmpty()) {
            reviewSessionStateRepository.deleteById(id)
            return null
        }

        return state.toSession()
    }

    fun forget(guildId: Long, reviewerId: Long) {
        reviewSessionStateRepository.deleteById(createId(guildId, reviewerId))
    }

    fun forgetOtherSessions(guildId: Long, reviewerId: Long): List<StoredReviewSession> {
        return reviewSessionStateRepository.findAll()
            .filterNotNull()
            .filter { it.guildId == guildId && it.reviewerId != reviewerId }
            .mapNotNull { state ->
                reviewSessionStateRepository.deleteById(state.id)
                state.toStoredSession()
            }
    }

    private fun createId(guildId: Long, reviewerId: Long): String = "$guildId:$reviewerId"

    private fun ReviewSessionState.toStoredSession(): StoredReviewSession? {
        if (pendingUserIds.isEmpty()) {
            return null
        }

        return StoredReviewSession(reviewerId, toSession())
    }

    private fun ReviewSessionState.toSession(): ReviewSession {
        return ReviewSession(
            pendingUserIds = pendingUserIds,
            oldestPendingUserId = oldestPendingUserId,
            approvedCount = approvedCount,
            rejectedCount = rejectedCount,
            manualActionCount = manualActionCount
        )
    }
}
