package be.duncanc.discordmodbot.member.gate

import be.duncanc.discordmodbot.member.gate.persistence.MemberGateReviewSessionState
import be.duncanc.discordmodbot.member.gate.persistence.MemberGateReviewSessionStateRepository
import org.springframework.stereotype.Component

@Component
class MemberGateReviewSessionRegistry(
    private val reviewSessionStateRepository: MemberGateReviewSessionStateRepository
) {
    fun remember(guildId: Long, reviewerId: Long, session: MemberGateReviewSession) {
        val pendingUserIds = session.toPendingUserIds()
        if (pendingUserIds.isEmpty()) {
            forget(guildId, reviewerId)
            return
        }

        reviewSessionStateRepository.save(
            MemberGateReviewSessionState(
                id = createId(guildId, reviewerId),
                guildId = guildId,
                reviewerId = reviewerId,
                oldestPendingUserId = session.oldestPendingUserId,
                pendingUserIds = pendingUserIds
            )
        )
    }

    fun get(guildId: Long, reviewerId: Long): MemberGateReviewSession? {
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

    private fun MemberGateReviewSessionState.toSession(): MemberGateReviewSession {
        return MemberGateReviewSession(
            pendingUserIds = pendingUserIds,
            oldestPendingUserId = oldestPendingUserId
        )
    }
}
