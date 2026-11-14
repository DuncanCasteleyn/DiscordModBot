package be.duncanc.discordmodbot.member.gate.persistence

import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

@Entity
@Table(name = "member_gate_attempts")
data class MemberGateAttempt(
    @Id
    val userId: Long,
    val attemptCount: Int,
    val lastModified: Instant
)
