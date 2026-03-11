package be.duncanc.discordmodbot.serverconfig.persistence


import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface ChannelOrderLockRepository : JpaRepository<ChannelOrderLock, Long>
