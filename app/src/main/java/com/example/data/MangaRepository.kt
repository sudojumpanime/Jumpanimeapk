package com.example.data

import com.example.data.local.MangaLibraryDao
import com.example.data.local.MangaReadHistoryDao
import com.example.data.model.MangaDetail
import com.example.data.model.MangaLibraryEntry
import com.example.data.model.MangaPage
import com.example.data.model.MangaReadHistory
import com.example.data.model.MangaSearchResult
import com.example.data.remote.MangaKakalotService
import kotlinx.coroutines.flow.Flow

class MangaRepository(
    private val service: MangaKakalotService,
    private val libraryDao: MangaLibraryDao,
    private val historyDao: MangaReadHistoryDao
) {
    suspend fun search(query: String, page: Int = 1): List<MangaSearchResult> =
        service.search(query, page)

    suspend fun getLatestUpdates(page: Int = 1): List<MangaSearchResult> =
        service.getLatestUpdates(page)

    suspend fun getDetail(mangaUrl: String): MangaDetail =
        service.getDetail(mangaUrl)

    suspend fun getChapterPages(chapterUrl: String): List<MangaPage> =
        service.getChapterPages(chapterUrl)

    fun getLibrary(): Flow<List<MangaLibraryEntry>> = libraryDao.getAll()

    suspend fun isInLibrary(mangaId: String): Boolean = libraryDao.getById(mangaId) != null

    suspend fun addToLibrary(entry: MangaLibraryEntry) = libraryDao.insert(entry)

    suspend fun removeFromLibrary(mangaId: String) = libraryDao.delete(mangaId)

    fun getReadHistory(mangaId: String): Flow<List<MangaReadHistory>> =
        historyDao.getHistoryForManga(mangaId)

    fun getAllHistory(): Flow<List<MangaReadHistory>> = historyDao.getAllHistory()

    suspend fun saveReadProgress(
        mangaId: String,
        mangaTitle: String,
        mangaCover: String,
        chapterId: String,
        chapterName: String,
        chapterUrl: String,
        lastPageRead: Int,
        isRead: Boolean = false
    ) {
        val existing = historyDao.getChapterHistory(mangaId, chapterId)
        if (existing != null) {
            historyDao.insert(
                existing.copy(
                    lastPageRead = lastPageRead,
                    isRead = isRead || existing.isRead,
                    readAt = System.currentTimeMillis()
                )
            )
        } else {
            historyDao.insert(
                MangaReadHistory(
                    mangaId = mangaId,
                    mangaTitle = mangaTitle,
                    mangaCover = mangaCover,
                    chapterId = chapterId,
                    chapterName = chapterName,
                    chapterUrl = chapterUrl,
                    lastPageRead = lastPageRead,
                    isRead = isRead
                )
            )
        }
    }

    suspend fun markChapterRead(mangaId: String, chapterId: String) =
        historyDao.markChapterRead(mangaId, chapterId)
}
