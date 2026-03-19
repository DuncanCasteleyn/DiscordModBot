package be.duncanc.discordmodbot.member.gate

import be.duncanc.discordmodbot.member.gate.persistence.ReviewPrompt
import be.duncanc.discordmodbot.member.gate.persistence.ReviewPromptRepository
import org.springframework.stereotype.Component

@Component
class ReviewPromptRegistry(
    private val reviewPromptRepository: ReviewPromptRepository
) {
    fun remember(guildId: Long, userId: Long, messageId: Long) {
        reviewPromptRepository.save(
            ReviewPrompt(
                id = ReviewPrompt.createId(guildId, userId),
                guildId = guildId,
                userId = userId,
                messageId = messageId
            )
        )
    }

    fun forget(guildId: Long, userId: Long): Long? {
        val id = ReviewPrompt.createId(guildId, userId)
        val prompt = reviewPromptRepository.findById(id).orElse(null) ?: return null
        reviewPromptRepository.deleteById(id)
        return prompt.messageId
    }
}
