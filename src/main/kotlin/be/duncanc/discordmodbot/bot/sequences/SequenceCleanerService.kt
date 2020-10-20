package be.duncanc.discordmodbot.bot.sequences

import be.duncanc.discordmodbot.bot.RunBots
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service

@Service
class SequenceCleanerService(
        private val runBots: RunBots
) {
    @Scheduled(fixedDelay = 1000 * 60)
    fun cleanExpiredSequences() {
        runBots.runningBots.forEach {
            it.registeredListeners.filter { listener ->
                listener::class == Sequence::class
            }.forEach { sequence ->
                sequence as Sequence
                if (sequence.sequenceIsExpired()) {
                    sequence.destroy()
                }
            }
        }
    }
}
