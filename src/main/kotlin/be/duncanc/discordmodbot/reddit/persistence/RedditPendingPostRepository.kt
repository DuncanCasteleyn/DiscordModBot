package be.duncanc.discordmodbot.reddit.persistence

import org.springframework.data.keyvalue.repository.KeyValueRepository
import org.springframework.stereotype.Repository

@Repository
interface RedditPendingPostRepository : KeyValueRepository<RedditPendingPost, String>
