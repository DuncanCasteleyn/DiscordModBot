package be.duncanc.discordmodbot.bot.sequences

import net.dv8tion.jda.api.JDA
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service

@Service
class SequenceCleanerService(
    private val jda: JDA
) {
    @Scheduled(fixedDelay = 1000L * 60L)
    fun cleanExpiredSequences() {
        jda.registeredListeners.filter { listener ->
            listener is Sequence && listener.expired
        }.forEach { sequence ->
            sequence as Sequence
            sequence.destroy()
        }
    }
}
