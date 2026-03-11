package be.duncanc.discordmodbot.logging.persistence


import org.springframework.data.keyvalue.repository.KeyValueRepository
import org.springframework.stereotype.Repository

@Repository
interface AttachmentProxyRepository : KeyValueRepository<AttachmentProxy, Long>
