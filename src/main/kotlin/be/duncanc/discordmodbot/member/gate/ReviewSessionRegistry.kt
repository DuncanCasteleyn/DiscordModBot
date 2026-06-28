package be.duncanc.discordmodbot.member.gate

import be.duncanc.discordmodbot.member.gate.persistence.ReviewSessionState
import be.duncanc.discordmodbot.member.gate.persistence.ReviewSessionStateRepository
import org.springframework.stereotype.Component
import java.time.Instant

@Component
class ReviewSessionRegistry(
    private val reviewSessionStateRepository: ReviewSessionStateRepository
) {
    data class StoredReviewSession(
        val reviewerId: Long,
        val session: ReviewSession,
        val updatedAt: Instant
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
                manualActionCount = session.manualActionCount,
                updatedAt = Instant.now()
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
        return getOtherSessions(guildId, reviewerId)
            .mapNotNull { state ->
                reviewSessionStateRepository.deleteById(createId(guildId, state.reviewerId))
                state
            }
    }

    fun forgetSessions(guildId: Long, reviewerIds: Set<Long>): List<StoredReviewSession> {
        if (reviewerIds.isEmpty()) {
            return emptyList()
        }

        return reviewSessionStateRepository.findAll()
            .filterNotNull()
            .filter { it.guildId == guildId && it.reviewerId in reviewerIds }
            .mapNotNull { state ->
                reviewSessionStateRepository.deleteById(state.id)
                state.toStoredSession()
            }
    }

    fun getOtherSessions(guildId: Long, reviewerId: Long): List<StoredReviewSession> {
        return reviewSessionStateRepository.findAll()
            .filterNotNull()
            .filter { it.guildId == guildId && it.reviewerId != reviewerId }
            .mapNotNull { it.toStoredSession() }
    }

    private fun createId(guildId: Long, reviewerId: Long): String = "$guildId:$reviewerId"

    private fun ReviewSessionState.toStoredSession(): StoredReviewSession? {
        if (pendingUserIds.isEmpty()) {
            return null
        }

        return StoredReviewSession(reviewerId, toSession(), updatedAt)
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
