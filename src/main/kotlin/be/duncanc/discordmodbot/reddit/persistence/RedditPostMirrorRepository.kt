package be.duncanc.discordmodbot.reddit.persistence

import org.springframework.data.keyvalue.repository.KeyValueRepository
import org.springframework.stereotype.Repository

@Repository
interface RedditPostMirrorRepository : KeyValueRepository<RedditPostMirror, String> {
    fun findAllByGuildId(guildId: Long): List<RedditPostMirror>
}
