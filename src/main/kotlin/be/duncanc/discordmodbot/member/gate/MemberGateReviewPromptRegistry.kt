package be.duncanc.discordmodbot.member.gate

import org.springframework.stereotype.Component
import java.util.concurrent.ConcurrentHashMap

@Component
class MemberGateReviewPromptRegistry {
    private val promptMessageIds = ConcurrentHashMap<Long, Long>()

    fun remember(userId: Long, messageId: Long) {
        promptMessageIds[userId] = messageId
    }

    fun forget(userId: Long): Long? = promptMessageIds.remove(userId)
}
