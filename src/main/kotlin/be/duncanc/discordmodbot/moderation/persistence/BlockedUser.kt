package be.duncanc.discordmodbot.moderation.persistence

import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table

@Entity
@Table(name = "blocked_users")
data class BlockedUser
    (
    @Id
    val userId: Long
)
