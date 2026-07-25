package com.example.data.remote

import kotlinx.serialization.Serializable
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

@Serializable
data class JikanResponse<T>(
    val data: T
)

@Serializable
data class JikanPaginationResponse<T>(
    val data: T? = null,
    val pagination: JikanPagination? = null
)

@Serializable
data class JikanPagination(
    val last_visible_page: Int? = null,
    val has_next_page: Boolean? = null
)

@Serializable
data class JikanAnime(
    val mal_id: Int = 0,
    val title: String = "",
    val title_english: String? = null,
    val title_japanese: String? = null,
    val images: JikanImages? = null,
    val score: Double? = null,
    val episodes: Int? = null,
    val status: String? = null,
    val type: String? = null,
    val genres: List<JikanGenre> = emptyList(),
    val explicit_genres: List<JikanGenre> = emptyList(),
    val themes: List<JikanGenre> = emptyList(),
    val demographics: List<JikanGenre> = emptyList(),
    val synopsis: String? = null,
    val year: Int? = null,
    val airing: Boolean = false,
    val broadcast: JikanBroadcast? = null,
    val aired: JikanAired? = null
)

@Serializable
data class JikanAired(
    val from: String? = null,
    val to: String? = null,
    val prop: JikanAiredProp? = null,
    val string: String? = null
)

@Serializable
data class JikanAiredProp(
    val from: JikanDateProp? = null,
    val to: JikanDateProp? = null
)

@Serializable
data class JikanDateProp(
    val day: Int? = null,
    val month: Int? = null,
    val year: Int? = null
)

@Serializable
data class JikanBroadcast(
    val day: String? = null,
    val time: String? = null,
    val timezone: String? = null,
    val string: String? = null
)

@Serializable
data class JikanImages(
    val jpg: JikanImageUrls? = null
)

@Serializable
data class JikanImageUrls(
    val image_url: String? = null,
    val large_image_url: String? = null
)

@Serializable
data class JikanGenre(
    val mal_id: Int? = null,
    val name: String? = null
)

@Serializable
data class JikanRecommendation(
    val entry: JikanAnimeSummary
)

@Serializable
data class JikanEpisode(
    val mal_id: Int = 0,
    val title: String? = null,
    val title_japanese: String? = null,
    val title_romanji: String? = null
)

@Serializable
data class JikanAnimeSummary(
    val mal_id: Int = 0,
    val title: String = "",
    val images: JikanImages? = null
)

interface JikanApiService {
    @GET("anime")
    suspend fun searchAnime(
        @Query("q") query: String?,
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 20,
        @Query("type") type: String? = null,
        @Query("status") status: String? = null,
        @Query("genres") genres: String? = null,
        @Query("order_by") orderBy: String? = "popularity",
        @Query("sort") sort: String? = "asc"
    ): JikanPaginationResponse<List<JikanAnime>>

    @GET("anime/{id}")
    suspend fun getAnimeDetail(@Path("id") id: Int): JikanResponse<JikanAnime>

    @GET("anime/{id}/recommendations")
    suspend fun getRecommendations(@Path("id") id: Int): JikanResponse<List<JikanRecommendation>>

    @GET("anime/{id}/episodes")
    suspend fun getAnimeEpisodes(
        @Path("id") id: Int,
        @Query("page") page: Int = 1
    ): JikanPaginationResponse<List<JikanEpisode>>

    @GET("schedules")
    suspend fun getSchedule(
        @Query("filter") filter: String? = null,
        @Query("page") page: Int? = null,
        @Query("limit") limit: Int? = null
    ): JikanPaginationResponse<List<JikanAnime>>
}
