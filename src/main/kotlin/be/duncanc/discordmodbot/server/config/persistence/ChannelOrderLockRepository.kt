package be.duncanc.discordmodbot.server.config.persistence


import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface ChannelOrderLockRepository : JpaRepository<ChannelOrderLock, Long>
