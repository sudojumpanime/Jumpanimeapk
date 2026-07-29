package com.example

import android.content.Context
import androidx.room.Room
import com.example.data.JumpAnimeRepository
import com.example.data.MangaRepository
import com.example.data.local.AppDatabase
import com.example.data.local.SessionManager
import com.example.data.remote.ApiProvider
import com.example.data.remote.MangaKakalotService

class DependencyContainer(context: Context) {
    val sessionManager by lazy { SessionManager(context) }
    val apiProvider by lazy { ApiProvider(sessionManager) }
    
    val database by lazy {
        Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "jumpanime_db"
        ).fallbackToDestructiveMigration().build()
    }
    
    val repository by lazy {
        JumpAnimeRepository(
            apiProvider,
            database.animeWatchDao(),
            database.animeDao(),
            database.searchHistoryDao(),
            database.customTitleDao(),
            database.commentDao(),
            sessionManager
        )
    }
    
    val episodeUpdateService by lazy {
        com.example.data.EpisodeUpdateService(context, repository, database.notificationDao())
    }

    val mangaService by lazy { MangaKakalotService() }

    val mangaRepository by lazy {
        MangaRepository(
            mangaService,
            database.mangaLibraryDao(),
            database.mangaReadHistoryDao()
        )
    }
}
