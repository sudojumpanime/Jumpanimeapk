package com.example.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow
import com.example.data.model.Anime
import com.example.data.model.SearchHistory
import com.example.data.model.MangaLibraryEntry
import com.example.data.model.MangaReadHistory

@Entity(
    tableName = "notifications",
    indices = [Index(value = ["timestamp"])]
)
data class NotificationEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val animeId: String,
    val animeTitle: String,
    val animePoster: String,
    val episodeNumber: Int,
    val episodeTitle: String,
    val malId: String? = null,
    val seasonId: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "custom_titles")
data class CustomTitle(
    @PrimaryKey val title: String,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "anime_watch_state")
data class AnimeWatchState(
    @PrimaryKey val animeId: String,
    val animeTitle: String? = null,
    val animePoster: String? = null,
    val seasonId: String? = null,
    val lastEpisode: Int,
    val episodeTitle: String? = null,
    val progress: Float = 0f,
    val timestamp: Long = System.currentTimeMillis()
)

@Dao
interface AnimeWatchDao {
    @Query("SELECT * FROM anime_watch_state WHERE animeId = :id")
    suspend fun getState(id: String): AnimeWatchState?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveState(state: AnimeWatchState)

    @Query("SELECT * FROM anime_watch_state ORDER BY timestamp DESC")
    suspend fun getAllStatesList(): List<AnimeWatchState>

    @Query("SELECT * FROM anime_watch_state ORDER BY timestamp DESC")
    fun getAllStates(): Flow<List<AnimeWatchState>>

    @Query("DELETE FROM anime_watch_state WHERE animeId = :id")
    suspend fun deleteState(id: String)
}

@Dao
interface AnimeDao {
    @Query("SELECT * FROM animes ORDER BY popularity DESC LIMIT 10")
    suspend fun getTrending(): List<Anime>

    @Query("SELECT * FROM animes WHERE featured = 1")
    suspend fun getFeatured(): List<Anime>

    @Query("SELECT * FROM animes WHERE recentlyAdded = 1")
    suspend fun getRecentlyAdded(): List<Anime>

    @Query("SELECT * FROM animes WHERE mostPopular = 1")
    suspend fun getMostPopular(): List<Anime>

    @Query("SELECT * FROM animes WHERE popularThisMonth = 1")
    suspend fun getPopularThisMonth(): List<Anime>

    @Query("SELECT * FROM animes WHERE popularThisWeek = 1")
    suspend fun getPopularThisWeek(): List<Anime>

    @Query("SELECT * FROM animes WHERE anilab2Data = 1")
    suspend fun getAnilab2Data(): List<Anime>

    @Query("SELECT * FROM animes WHERE inWatchlist = 1")
    fun getWatchlistFlow(): Flow<List<Anime>>

    @Query("SELECT * FROM animes WHERE inWatchlist = 1")
    suspend fun getWatchlist(): List<Anime>

    @Query("SELECT * FROM animes")
    suspend fun getAll(): List<Anime>

    @Query("SELECT * FROM animes WHERE _id = :id")
    suspend fun getById(id: String): Anime?

    @Query("UPDATE animes SET inWatchlist = :inWatchlist WHERE _id = :id")
    suspend fun updateInWatchlist(id: String, inWatchlist: Boolean)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(animes: List<Anime>)

    @Query("DELETE FROM animes")
    suspend fun deleteAll()

    @Query("UPDATE animes SET inWatchlist = 0 WHERE _id NOT LIKE 'mal_%'")
    suspend fun clearWatchlistFlag()

    @Query("UPDATE animes SET inWatchlist = 1 WHERE _id = :id")
    suspend fun addToWatchlist(id: String)

    @Query("UPDATE animes SET inWatchlist = 0 WHERE _id = :id")
    suspend fun removeFromWatchlist(id: String)
}

@Dao
interface SearchHistoryDao {
    @Query("SELECT * FROM search_history ORDER BY timestamp DESC LIMIT 10")
    fun getRecentSearches(): Flow<List<SearchHistory>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSearch(search: SearchHistory)

    @Query("DELETE FROM search_history WHERE query = :query")
    suspend fun deleteSearch(query: String)
}

@Dao
interface NotificationDao {
    @Query("SELECT * FROM notifications ORDER BY timestamp DESC")
    fun getAllNotifications(): Flow<List<NotificationEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNotification(notification: NotificationEntity)

    @Query("DELETE FROM notifications")
    suspend fun deleteAll()

    @Query("DELETE FROM notifications WHERE id = :id")
    suspend fun deleteById(id: Int)
}

@Dao
interface CustomTitleDao {
    @Query("SELECT * FROM custom_titles ORDER BY timestamp DESC")
    fun getAllCustomTitles(): Flow<List<CustomTitle>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCustomTitle(title: CustomTitle)

    @Query("DELETE FROM custom_titles WHERE title = :title")
    suspend fun deleteCustomTitle(title: String)

    @Query("DELETE FROM custom_titles")
    suspend fun deleteAll()
}

@Entity(
    tableName = "comments",
    indices = [
        Index(value = ["animeId"]),
        Index(value = ["parentId"]),
        Index(value = ["animeId", "timestamp"])
    ]
)
data class Comment(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val remoteId: String = "",           // MongoDB _id from server
    val animeId: String,
    val userId: String = "",             // server userId
    val username: String,
    val userAvatar: String,
    val text: String,
    val timestamp: Long = System.currentTimeMillis(),
    val likes: Int = 0,
    val isLikedByUser: Boolean = false,
    val parentId: String? = null,        // remoteId of parent; null = top-level
    val replyCount: Int = 0,
    val isAuthorAdmin: Boolean = false   // renders blue verified tick in UI
)

@Dao
interface CommentDao {
    @Query("SELECT * FROM comments WHERE animeId = :animeId ORDER BY timestamp DESC")
    fun getCommentsForAnime(animeId: String): Flow<List<Comment>>

    @Query("SELECT * FROM comments WHERE id = :id")
    suspend fun getById(id: Int): Comment?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertComment(comment: Comment)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(comments: List<Comment>)

    @Query("DELETE FROM comments WHERE animeId = :animeId")
    suspend fun deleteAllForAnime(animeId: String)

    @Query("DELETE FROM comments WHERE id = :commentId")
    suspend fun deleteComment(commentId: Int)

    @Query("DELETE FROM comments WHERE parentId = :parentRemoteId")
    suspend fun deleteReplies(parentRemoteId: String)

    @Query("UPDATE comments SET likes = likes + :delta, isLikedByUser = :isLiked WHERE id = :commentId")
    suspend fun updateLike(commentId: Int, delta: Int, isLiked: Boolean)

    @Query("UPDATE comments SET likes = :likes, isLikedByUser = :isLiked WHERE id = :commentId")
    suspend fun updateLikeExact(commentId: Int, likes: Int, isLiked: Boolean)
}

@Database(entities = [AnimeWatchState::class, Anime::class, SearchHistory::class, CustomTitle::class, NotificationEntity::class, Comment::class, MangaLibraryEntry::class, MangaReadHistory::class], version = 21, exportSchema = false)
@TypeConverters(RoomConverters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun animeWatchDao(): AnimeWatchDao
    abstract fun animeDao(): AnimeDao
    abstract fun searchHistoryDao(): SearchHistoryDao
    abstract fun customTitleDao(): CustomTitleDao
    abstract fun notificationDao(): NotificationDao
    abstract fun commentDao(): CommentDao
    abstract fun mangaLibraryDao(): MangaLibraryDao
    abstract fun mangaReadHistoryDao(): MangaReadHistoryDao
}
