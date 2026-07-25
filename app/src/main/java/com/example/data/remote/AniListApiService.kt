package com.example.data.remote

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName
import retrofit2.http.Body
import retrofit2.http.POST

@Serializable
data class AniListVariables(
    @SerialName("page") val page: Int = 1,
    @SerialName("perPage") val perPage: Int = 20,
    @SerialName("airingAtGreater") val airingAtGreater: Int = 0
)

@Serializable
data class AniListGraphQLRequest(
    @SerialName("query") val query: String = "",
    @SerialName("variables") val variables: AniListVariables = AniListVariables()
)

@Serializable
data class AniListGraphQLResponse(
    @SerialName("data") val data: AniListData? = null,
    @SerialName("errors") val errors: List<AniListError>? = null
)

@Serializable
data class AniListError(
    @SerialName("message") val message: String = ""
)

@Serializable
data class AniListData(
    @SerialName("Page") val Page: AniListPage? = null
)

@Serializable
data class AniListPage(
    @SerialName("airingSchedules") val airingSchedules: List<AniListAiringSchedule>? = null
)

@Serializable
data class AniListAiringSchedule(
    @SerialName("id") val id: Int = 0,
    @SerialName("airingAt") val airingAt: Long = 0L,
    @SerialName("episode") val episode: Int = 0,
    @SerialName("media") val media: AniListMedia? = null
)

@Serializable
data class NextAiringEpisode(
    @SerialName("episode") val episode: Int? = null,
    @SerialName("airingAt") val airingAt: Long? = null
)

@Serializable
data class AniListMedia(
    @SerialName("id") val id: Int = 0,
    @SerialName("idMal") val idMal: Int? = null,
    @SerialName("title") val title: AniListTitle? = null,
    @SerialName("coverImage") val coverImage: AniListCoverImage? = null,
    @SerialName("genres") val genres: List<String> = emptyList(),
    @SerialName("tags") val tags: List<AniListTag>? = null,
    @SerialName("averageScore") val averageScore: Int? = null,
    @SerialName("isAdult") val isAdult: Boolean? = null,
    @SerialName("status") val status: String? = null,
    @SerialName("type") val type: String? = null,
    @SerialName("episodes") val episodes: Int? = null,
    @SerialName("nextAiringEpisode") val nextAiringEpisode: NextAiringEpisode? = null
)

@Serializable
data class AniListTag(
    @SerialName("name") val name: String? = null
)

@Serializable
data class AniListTitle(
    @SerialName("romaji") val romaji: String? = null,
    @SerialName("english") val english: String? = null,
    @SerialName("native") val native: String? = null
)

@Serializable
data class AniListCoverImage(
    @SerialName("large") val large: String? = null
)

@Serializable
data class AniListGraphQLSearchRequest(
    @SerialName("query") val query: String,
    @SerialName("variables") val variables: kotlinx.serialization.json.JsonObject
)

@Serializable
data class AniListSearchVariables(
    @SerialName("search") val search: String? = null,
    @SerialName("page") val page: Int = 1,
    @SerialName("perPage") val perPage: Int = 20,
    @SerialName("genre") val genre: String? = null,
    @SerialName("tag") val tag: String? = null,
    @SerialName("type") val type: String? = null,
    @SerialName("status") val status: String? = null,
    @SerialName("sort") val sort: List<String>? = null
)

@Serializable
data class AniListGraphQLSearchResponse(
    @SerialName("data") val data: AniListSearchData? = null,
    @SerialName("errors") val errors: List<AniListError>? = null
)

@Serializable
data class AniListSearchData(
    @SerialName("Page") val Page: AniListSearchPage? = null
)

@Serializable
data class AniListSearchPage(
    @SerialName("media") val media: List<AniListMedia>? = null,
    @SerialName("airingSchedules") val airingSchedules: List<AniListAiringSchedule>? = null
)

interface AniListApiService {
    @POST("/")
    suspend fun getUpcomingSchedules(
        @Body request: AniListGraphQLRequest
    ): AniListGraphQLResponse

    @POST("/")
    suspend fun searchAnimeGraphQL(
        @Body request: AniListGraphQLSearchRequest
    ): AniListGraphQLSearchResponse
}
