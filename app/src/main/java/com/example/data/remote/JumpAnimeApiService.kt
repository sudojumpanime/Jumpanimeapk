package com.example.data.remote

import com.example.data.model.*
import retrofit2.Response
import retrofit2.http.*

interface JumpAnimeApiService {
    @POST("api/auth/login")
    suspend fun login(@Body request: Map<String, String>): Response<AuthResponse>

    @POST("api/auth/register")
    suspend fun register(@Body request: Map<String, String>): Response<AuthResponse>

    @POST("api/auth/resend-verification")
    suspend fun resendVerification(@Body request: Map<String, String>): Response<AuthResponse>

    @GET("api/anime")
    suspend fun getAnimes(
        @Query("limit") limit: Int = 20,
        @Query("page") page: Int = 1,
        @Query("genre") genre: String? = null,
        @Query("type") type: String? = null,
        @Query("status") status: String? = null,
        @Query("sort") sort: String? = null,
        @Query("featured") featured: Boolean? = null,
        @Query("search") search: String? = null
    ): AnimeListResponse

    @GET("api/anime/trending/auto")
    suspend fun getTrendingAuto(): AnimeListResponse

    @GET("api/anime/{id}")
    suspend fun getAnimeDetail(@Path("id") id: String): Anime

    @GET("api/episodes/{seasonId}")
    suspend fun getEpisodes(@Path("seasonId") seasonId: String): EpisodeListResponse

    @GET("api/episodes/{seasonId}/{epNumber}")
    suspend fun getEpisode(
        @Path("seasonId") seasonId: String,
        @Path("epNumber") epNumber: Int
    ): EpisodeResponse

    @POST("api/user/history")
    suspend fun saveHistory(@Body request: PlaybackHistoryRequest): Response<Unit>

    @POST("api/anime/{animeId}/watch")
    suspend fun recordWatchCount(@Path("animeId") animeId: String): Response<Unit>

    @GET("api/user/list")
    suspend fun getWatchlist(): WatchlistResponse

    @POST("api/user/list/{animeId}")
    suspend fun addToWatchlist(@Path("animeId") animeId: String): Response<Unit>

    @DELETE("api/user/list/{animeId}")
    suspend fun removeFromWatchlist(@Path("animeId") animeId: String): Response<Unit>

    @GET("api/user/history/continue")
    suspend fun getContinueWatching(): ContinueWatchingResponse

    @GET("api/comments/{animeId}")
    suspend fun getComments(@Path("animeId") animeId: String): CommentsResponse

    @POST("api/comments/{animeId}")
    suspend fun postComment(
        @Path("animeId") animeId: String,
        @Body request: PostCommentRequest
    ): Response<PostCommentResponse>

    /** Toggle like — returns updated likesCount and isLikedByUser */
    @POST("api/comments/{commentId}/like")
    suspend fun likeComment(@Path("commentId") commentId: String): LikeResponse

    @DELETE("api/comments/{commentId}")
    suspend fun deleteComment(@Path("commentId") commentId: String): Response<Unit>
}
