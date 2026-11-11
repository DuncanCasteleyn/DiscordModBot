package be.duncanc.discordmodbot.member.gate.persistence

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.time.Instant

@Repository
interface MemberGateAttemptRepository : JpaRepository<MemberGateAttempt, Long> {
    fun deleteByLastModifiedBefore(lastModified: Instant): Long
}
