package be.duncanc.discordmodbot.data.repositories.key.value

import be.duncanc.discordmodbot.data.redis.hash.AttachmentProxy
import org.springframework.data.keyvalue.repository.KeyValueRepository
import org.springframework.stereotype.Repository

@Repository
interface AttachmentProxyRepository : KeyValueRepository<AttachmentProxy, Long>
