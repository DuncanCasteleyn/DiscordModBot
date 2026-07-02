package be.duncanc.discordmodbot.reddit

import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import org.w3c.dom.Element
import java.io.ByteArrayInputStream
import java.time.Instant
import javax.xml.parsers.DocumentBuilderFactory

@Component
class RedditRssClient(
    private val redditRestClient: RestClient
) {
    fun fetchNewestPosts(subreddit: String): List<RedditPost> {
        val feed = redditRestClient.get()
            .uri("/r/{subreddit}/new/.rss", subreddit)
            .retrieve()
            .body(String::class.java)
            ?: throw IllegalStateException("Reddit RSS response was empty")

        return parse(feed)
    }

    internal fun parse(feed: String): List<RedditPost> {
        val factory = DocumentBuilderFactory.newInstance()
        factory.isNamespaceAware = true
        val document = factory.newDocumentBuilder().parse(ByteArrayInputStream(feed.toByteArray(Charsets.UTF_8)))
        val entries = document.getElementsByTagNameNS(ATOM_NAMESPACE, "entry")
        return (0 until entries.length).map { index ->
            val entry = entries.item(index) as Element
            RedditPost(
                id = entry.text("id"),
                title = entry.text("title"),
                author = entry.child("author")?.text("name")?.removePrefix("/u/"),
                permalink = entry.child("link")?.getAttribute("href") ?: "",
                publishedAt = Instant.parse(entry.text("published")),
                thumbnailUrl = entry.child(MEDIA_NAMESPACE, "thumbnail")?.getAttribute("url")?.takeIf { it.isNotBlank() }
            )
        }.filter { it.id.isNotBlank() && it.permalink.isNotBlank() }
    }

    private fun Element.text(localName: String): String {
        return child(localName)?.textContent?.trim().orEmpty()
    }

    private fun Element.child(localName: String): Element? {
        return child(ATOM_NAMESPACE, localName)
    }

    private fun Element.child(namespace: String, localName: String): Element? {
        val children = getElementsByTagNameNS(namespace, localName)
        return children.item(0) as? Element
    }

    companion object {
        private const val ATOM_NAMESPACE = "http://www.w3.org/2005/Atom"
        private const val MEDIA_NAMESPACE = "http://search.yahoo.com/mrss/"
    }
}

data class RedditPost(
    val id: String,
    val title: String,
    val author: String?,
    val permalink: String,
    val publishedAt: Instant,
    val thumbnailUrl: String?
)
