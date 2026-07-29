package com.example.data.local

import androidx.room.*
import com.example.data.model.MangaLibraryEntry
import com.example.data.model.MangaReadHistory
import kotlinx.coroutines.flow.Flow

@Dao
interface MangaLibraryDao {
    @Query("SELECT * FROM manga_library ORDER BY addedAt DESC")
    fun getAll(): Flow<List<MangaLibraryEntry>>

    @Query("SELECT * FROM manga_library WHERE mangaId = :id")
    suspend fun getById(id: String): MangaLibraryEntry?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: MangaLibraryEntry)

    @Query("DELETE FROM manga_library WHERE mangaId = :id")
    suspend fun delete(id: String)
}

@Dao
interface MangaReadHistoryDao {
    @Query("SELECT * FROM manga_read_history WHERE mangaId = :mangaId ORDER BY readAt DESC")
    fun getHistoryForManga(mangaId: String): Flow<List<MangaReadHistory>>

    @Query("SELECT * FROM manga_read_history ORDER BY readAt DESC")
    fun getAllHistory(): Flow<List<MangaReadHistory>>

    @Query("SELECT * FROM manga_read_history WHERE mangaId = :mangaId AND chapterId = :chapterId LIMIT 1")
    suspend fun getChapterHistory(mangaId: String, chapterId: String): MangaReadHistory?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(history: MangaReadHistory)

    @Query("UPDATE manga_read_history SET isRead = 1 WHERE mangaId = :mangaId AND chapterId = :chapterId")
    suspend fun markChapterRead(mangaId: String, chapterId: String)

    @Query("UPDATE manga_read_history SET lastPageRead = :page WHERE id = :id")
    suspend fun updatePage(id: Int, page: Int)

    @Query("DELETE FROM manga_read_history WHERE mangaId = :mangaId AND chapterId = :chapterId")
    suspend fun deleteChapterHistory(mangaId: String, chapterId: String)
}
