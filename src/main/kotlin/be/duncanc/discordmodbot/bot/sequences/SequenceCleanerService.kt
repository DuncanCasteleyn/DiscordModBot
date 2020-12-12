package be.duncanc.discordmodbot.bot.sequences

import net.dv8tion.jda.api.JDA
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service

@Service
class SequenceCleanerService(
    private val jda: JDA
) {
    @Scheduled(fixedDelay = 1000 * 60)
    fun cleanExpiredSequences() {
        jda.registeredListeners.filter { listener ->
            listener::class == Sequence::class
        }.forEach { sequence ->
            sequence as Sequence
            if (sequence.sequenceIsExpired()) {
                sequence.destroy()
            }
        }
    }
}
