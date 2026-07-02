package be.duncanc.discordmodbot.reddit

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.client.JdkClientHttpRequestFactory
import org.springframework.web.client.RestClient

@Configuration
class RedditRssClientConfig {
    companion object {
        private const val BASE_URL = "https://www.reddit.com"
    }

    @Bean
    fun redditRestClient(
        redditProperties: RedditProperties,
        restClientBuilder: RestClient.Builder
    ): RestClient {
        val requestFactory = JdkClientHttpRequestFactory()
        requestFactory.setReadTimeout(redditProperties.readTimeout)

        return restClientBuilder
            .baseUrl(BASE_URL)
            .defaultHeader("User-Agent", redditProperties.userAgent)
            .requestFactory(requestFactory)
            .build()
    }
}
