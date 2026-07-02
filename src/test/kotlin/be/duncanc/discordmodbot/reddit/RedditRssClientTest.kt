package be.duncanc.discordmodbot.reddit

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.springframework.web.client.RestClient
import java.time.Instant

class RedditRssClientTest {
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
