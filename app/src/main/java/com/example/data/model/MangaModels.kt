package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Serializable
data class MangaSearchResult(
    val id: String,
    val title: String,
    val coverUrl: String,
    val url: String
)

@Serializable
data class MangaDetail(
    val id: String,
    val title: String,
    val coverUrl: String,
    val description: String,
    val author: String,
    val status: String,
    val genres: List<String>,
    val url: String,
    val chapters: List<MangaChapter>
)

@Serializable
data class MangaChapter(
    val id: String,
    val name: String,
    val url: String,
    val uploadedDate: String = ""
)

@Serializable
data class MangaPage(
    val index: Int,
    val imageUrl: String
)

@Entity(tableName = "manga_library")
data class MangaLibraryEntry(
    @PrimaryKey val mangaId: String,
    val title: String,
    val coverUrl: String,
    val url: String,
    val addedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "manga_read_history",
    indices = [androidx.room.Index(value = ["mangaId"])]
)
data class MangaReadHistory(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val mangaId: String,
    val mangaTitle: String,
    val mangaCover: String,
    val chapterId: String,
    val chapterName: String,
    val chapterUrl: String,
    val lastPageRead: Int = 0,
    val isRead: Boolean = false,
    val readAt: Long = System.currentTimeMillis()
)
