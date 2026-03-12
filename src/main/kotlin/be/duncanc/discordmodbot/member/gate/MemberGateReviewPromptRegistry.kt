package be.duncanc.discordmodbot.member.gate

import be.duncanc.discordmodbot.member.gate.persistence.MemberGateReviewPrompt
import be.duncanc.discordmodbot.member.gate.persistence.MemberGateReviewPromptRepository
import org.springframework.stereotype.Component

@Component
class MemberGateReviewPromptRegistry(
    private val reviewPromptRepository: MemberGateReviewPromptRepository
) {
    fun remember(userId: Long, messageId: Long) {
        reviewPromptRepository.save(MemberGateReviewPrompt(userId = userId, messageId = messageId))
    }

    fun forget(userId: Long): Long? {
        val prompt = reviewPromptRepository.findById(userId).orElse(null) ?: return null
        reviewPromptRepository.deleteById(userId)
        return prompt.messageId
    }
}
