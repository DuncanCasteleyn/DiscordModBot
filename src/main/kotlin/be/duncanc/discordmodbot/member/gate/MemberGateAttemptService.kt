package be.duncanc.discordmodbot.member.gate

import be.duncanc.discordmodbot.member.gate.persistence.MemberGateAttempt
import be.duncanc.discordmodbot.member.gate.persistence.MemberGateAttemptRepository
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Duration
import java.time.Instant

private val logger = LoggerFactory.getLogger(MemberGateAttemptService::class.java)

@Service
@Transactional(readOnly = true)
class MemberGateAttemptService(
    private val memberGateAttemptRepository: MemberGateAttemptRepository
) {
    companion object {
        private val RETENTION_PERIOD: Duration = Duration.ofDays(90)
    }

    @Transactional
    fun recordAttempt(userId: Long) {
        val attempt = memberGateAttemptRepository.findById(userId).orElse(null)
        val updated = if (attempt == null) {
            MemberGateAttempt(userId = userId, attemptCount = 1, lastModified = Instant.now())
        } else {
            attempt.copy(attemptCount = attempt.attemptCount + 1, lastModified = Instant.now())
        }
        memberGateAttemptRepository.save(updated)
    }

    @Transactional
    fun clearAttempts(userId: Long) {
        memberGateAttemptRepository.deleteById(userId)
    }

    /**
     * @return the number of attempts since the last time the member was let in, or null when no record exists.
     */
    fun getAttemptCount(userId: Long): Int? {
        return memberGateAttemptRepository.findById(userId).orElse(null)?.attemptCount
    }

    @Transactional
    @Scheduled(cron = "@daily")
    fun deleteStaleAttempts() {
        val cutoff = Instant.now().minus(RETENTION_PERIOD)
        val deletedCount = memberGateAttemptRepository.deleteByLastModifiedBefore(cutoff)
        if (deletedCount > 0) {
            logger.info("Deleted {} member gate attempt record(s) not modified since {}", deletedCount, cutoff)
        }
    }
}
