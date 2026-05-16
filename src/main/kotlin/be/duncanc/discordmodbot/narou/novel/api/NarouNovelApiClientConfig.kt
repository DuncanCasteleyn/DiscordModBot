package be.duncanc.discordmodbot.narou.novel.api

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.client.JdkClientHttpRequestFactory
import org.springframework.web.client.RestClient
import org.springframework.web.client.support.RestClientAdapter
import org.springframework.web.service.invoker.HttpServiceProxyFactory
import org.springframework.web.service.invoker.createClient
import java.time.Duration

@Configuration
class NarouNovelApiClientConfig {
    companion object {
        private const val BASE_URL = "https://api.syosetu.com"
    }

    @Bean
    fun narouNovelApiClient(restClientBuilder: RestClient.Builder): NarouNovelApiClient {
        val requestFactory = JdkClientHttpRequestFactory()

        requestFactory.setReadTimeout(Duration.ofSeconds(10))

        val restClient = restClientBuilder
            .baseUrl(BASE_URL)
            .requestFactory(requestFactory)
            .build()

        val proxyFactory = HttpServiceProxyFactory
            .builderFor(
                RestClientAdapter.create(restClient)
            )
            .build()

        return proxyFactory.createClient<NarouNovelApiClient>()
    }
}
