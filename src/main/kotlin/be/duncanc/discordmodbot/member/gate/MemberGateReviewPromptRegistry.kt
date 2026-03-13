package be.duncanc.discordmodbot.member.gate

import be.duncanc.discordmodbot.member.gate.persistence.MemberGateReviewPrompt
import be.duncanc.discordmodbot.member.gate.persistence.MemberGateReviewPromptRepository
import org.springframework.stereotype.Component

@Component
class MemberGateReviewPromptRegistry(
    private val reviewPromptRepository: MemberGateReviewPromptRepository
) {
    fun remember(guildId: Long, userId: Long, messageId: Long) {
        reviewPromptRepository.save(
            MemberGateReviewPrompt(
                id = MemberGateReviewPrompt.createId(guildId, userId),
                guildId = guildId,
                userId = userId,
                messageId = messageId
            )
        )
    }

    fun forget(guildId: Long, userId: Long): Long? {
        val id = MemberGateReviewPrompt.createId(guildId, userId)
        val prompt = reviewPromptRepository.findById(id).orElse(null) ?: return null
        reviewPromptRepository.deleteById(id)
        return prompt.messageId
    }
}
