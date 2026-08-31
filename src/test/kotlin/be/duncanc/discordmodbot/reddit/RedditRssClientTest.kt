package be.duncanc.discordmodbot.reddit

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.web.client.RestClient
import java.time.Instant

class RedditRssClientTest {
    @Test
    fun `fetch requests newest feed with max limit`() {
        val redditRestClient = mock<RestClient>()
        val uriSpec = mock<RestClient.RequestHeadersUriSpec<*>>()
        val headersSpec = mock<RestClient.RequestHeadersSpec<*>>()
        val responseSpec = mock<RestClient.ResponseSpec>()
        val client = RedditRssClient(redditRestClient = redditRestClient)
        whenever(redditRestClient.get()).thenReturn(uriSpec)
        doReturn(headersSpec).whenever(uriSpec).uri("/r/{subreddit}/new/.rss?limit={limit}", "Re_Zero", 100)
        whenever(headersSpec.retrieve()).thenReturn(responseSpec)
        whenever(responseSpec.body(String::class.java)).thenReturn("<feed/>")

        val posts = client.fetchNewestPosts("Re_Zero")

        verify(uriSpec).uri("/r/{subreddit}/new/.rss?limit={limit}", "Re_Zero", 100)
        assertEquals(emptyList<RedditPost>(), posts)
    }

    @Test
    fun `parse rejects feeds with doctype declarations`() {
        val client = RedditRssClient(
            redditRestClient = mock<RestClient>()
        )

        assertThrows(Exception::class.java) {
            client.parse(
                """
                <?xml version="1.0" encoding="UTF-8"?>
                <!DOCTYPE feed [
                  <!ENTITY xxe SYSTEM "file:///etc/passwd">
                ]>
                <feed xmlns="http://www.w3.org/2005/Atom">
                  <entry>
                    <id>t3_xxe</id>
                    <title>XXE</title>
                    <published>2026-07-02T21:29:12+00:00</published>
                  </entry>
                </feed>
                """.trimIndent()
            )
        }
    }

    @Test
    fun `parse skips entries with malformed published timestamp`() {
        val client = RedditRssClient(
            redditRestClient = mock<RestClient>()
        )

        val posts = client.parse(
            """
            <?xml version="1.0" encoding="UTF-8"?>
            <feed xmlns="http://www.w3.org/2005/Atom">
                <entry>
                    <author><name>/u/Subaru</name></author>
                    <id>t3_invalid</id>
                    <link href="https://www.reddit.com/r/Re_Zero/comments/invalid/title/" />
                    <published>not-a-timestamp</published>
                    <title>Invalid timestamp</title>
                </entry>
                <entry>
                    <author><name>/u/Rem</name></author>
                    <id>t3_valid</id>
                    <link href="https://www.reddit.com/r/Re_Zero/comments/valid/title/" />
                    <published>2026-07-02T21:29:12+00:00</published>
                    <title>Valid timestamp</title>
                </entry>
            </feed>
            """.trimIndent()
        )

        assertEquals(1, posts.size)
        assertEquals("t3_valid", posts.first().id)
    }

    @Test
    fun `parse reads reddit atom entries`() {
        val client = RedditRssClient(
            redditRestClient = mock<RestClient>()
        )

        val posts = client.parse(
            """
            <?xml version="1.0" encoding="UTF-8"?>
            <feed xmlns="http://www.w3.org/2005/Atom" xmlns:media="http://search.yahoo.com/mrss/">
                <entry>
                    <author><name>/u/Subaru</name></author>
                    <id>t3_abc123</id>
                    <media:thumbnail url="https://preview.redd.it/image.jpeg" />
                    <link href="https://www.reddit.com/r/Re_Zero/comments/abc123/title/" />
                    <published>2026-07-02T21:29:12+00:00</published>
                    <title>[media] Test post</title>
                </entry>
            </feed>
            """.trimIndent()
        )

        assertEquals(1, posts.size)
        assertEquals("t3_abc123", posts.first().id)
        assertEquals("[media] Test post", posts.first().title)
        assertEquals("Subaru", posts.first().author)
        assertEquals("https://www.reddit.com/r/Re_Zero/comments/abc123/title/", posts.first().permalink)
        assertEquals(Instant.parse("2026-07-02T21:29:12Z"), posts.first().publishedAt)
        assertEquals("https://preview.redd.it/image.jpeg", posts.first().thumbnailUrl)
    }
}
