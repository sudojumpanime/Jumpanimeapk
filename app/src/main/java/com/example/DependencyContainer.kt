package com.example

import android.content.Context
import androidx.room.Room
import com.example.data.JumpAnimeRepository
import com.example.data.local.AppDatabase
import com.example.data.local.SessionManager
import com.example.data.remote.ApiProvider

class DependencyContainer(context: Context) {
    val sessionManager by lazy { SessionManager(context) }
    val apiProvider by lazy { ApiProvider(sessionManager) { kitty.account.accessToken } }
    
    val database by lazy {
        Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "jumpanime_db"
        ).fallbackToDestructiveMigration().build()
    }
    
    val kitty by lazy { com.example.kitty.KittyCatalog(context) }
    val streamServers by lazy { com.example.kitty.servers.ServerCatalog(context) }
    val downloads by lazy { com.example.kitty.download.KittyDownloadManager(context, streamServers) }

    val repository by lazy {
        JumpAnimeRepository(
            apiProvider,
            database.animeWatchDao(),
            database.animeDao(),
            database.searchHistoryDao(),
            database.customTitleDao(),
            database.commentDao(),
            sessionManager,
            kitty
        )
    }
    
    val episodeUpdateService by lazy {
        com.example.data.EpisodeUpdateService(context, repository, database.notificationDao())
    }
}
