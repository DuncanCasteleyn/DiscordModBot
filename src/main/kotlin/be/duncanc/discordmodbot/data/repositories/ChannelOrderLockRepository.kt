package be.duncanc.discordmodbot.data.repositories

import be.duncanc.discordmodbot.data.entities.ChannelOrderLock
import org.springframework.data.repository.CrudRepository
import org.springframework.stereotype.Repository

@Repository
interface ChannelOrderLockRepository : CrudRepository<ChannelOrderLock, Long>
