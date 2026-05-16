package be.duncanc.discordmodbot.narou.novel.api

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import org.springframework.web.service.annotation.GetExchange

interface NarouNovelApiClient {
    @GetExchange("/novelapi/api/?ncode=n2267be&out=json&of=l-ti-gl-ga-nu-ua")
    fun fetchNovel(): List<NarouNovelApiResponseEntry>
}

@JsonIgnoreProperties(ignoreUnknown = true)
data class NarouNovelApiResponseEntry(
    @JsonProperty("general_lastup")
    val generalLastup: String? = null,
    @JsonProperty("general_all_no")
    val generalAllNo: Int? = null,
    val length: Long? = null,
    val time: Int? = null,
    @JsonProperty("novelupdated_at")
    val novelUpdatedAt: String? = null,
    @JsonProperty("updated_at")
    val updatedAt: String? = null
)
