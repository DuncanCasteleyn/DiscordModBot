package be.duncanc.discordmodbot.member.gate

import be.duncanc.discordmodbot.member.gate.persistence.ReviewSessionState
import be.duncanc.discordmodbot.member.gate.persistence.ReviewSessionStateRepository
import org.springframework.stereotype.Component

@Component
class ReviewSessionRegistry(
    private val reviewSessionStateRepository: ReviewSessionStateRepository
) {
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
                pendingUserIds = pendingUserIds
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

    private fun createId(guildId: Long, reviewerId: Long): String = "$guildId:$reviewerId"

    private fun ReviewSessionState.toSession(): ReviewSession {
        return ReviewSession(
            pendingUserIds = pendingUserIds,
            oldestPendingUserId = oldestPendingUserId
        )
    }
}
