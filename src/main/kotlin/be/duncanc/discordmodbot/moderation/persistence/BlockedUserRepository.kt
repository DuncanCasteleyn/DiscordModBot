package be.duncanc.discordmodbot.moderation.persistence

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface BlockedUserRepository : JpaRepository<BlockedUser, Long>
