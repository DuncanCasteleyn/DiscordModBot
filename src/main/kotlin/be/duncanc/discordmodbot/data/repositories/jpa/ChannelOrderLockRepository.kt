package be.duncanc.discordmodbot.data.repositories.jpa

import be.duncanc.discordmodbot.data.entities.ChannelOrderLock
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface ChannelOrderLockRepository : JpaRepository<ChannelOrderLock, Long>
