package com.example.utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

sealed interface UpdateState {
    object Idle : UpdateState
    object Checking : UpdateState
    data class UpdateAvailable(
        val tagName: String,
        val body: String,
        val downloadUrl: String,
        val fileName: String
    ) : UpdateState
    object NoUpdate : UpdateState
    data class Downloading(val progress: Float) : UpdateState
    data class Success(val apkFile: File) : UpdateState
    data class Error(val message: String) : UpdateState
}

object UpdateManager {
    private const val TAG = "UpdateManager"
    
    private val _updateState = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val updateState: StateFlow<UpdateState> = _updateState.asStateFlow()

    private const val PREF_NAME = "JumpAnimeUpdates"
    private const val KEY_REPO = "github_repo"
    private const val DEFAULT_REPO = "sudojumpanime/jumpanime"

    fun getGithubRepo(context: Context): String {
        return DEFAULT_REPO
    }

    fun saveGithubRepo(context: Context, repo: String) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_REPO, repo.trim()).apply()
    }

    fun resetState() {
        _updateState.value = UpdateState.Idle
    }

    suspend fun checkForUpdates(context: Context) {
        _updateState.value = UpdateState.Checking
        val repo = getGithubRepo(context)
        
        withContext(Dispatchers.IO) {
            try {
                val url = URL("https://api.github.com/repos/$repo/releases/latest")
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.setRequestProperty("User-Agent", "JumpAnime-App")
                connection.setRequestProperty("Accept", "application/vnd.github.v3+json")
                connection.connectTimeout = 10000
                connection.readTimeout = 10000

                val responseCode = connection.responseCode
                if (responseCode == HttpURLConnection.HTTP_NOT_FOUND) {
                    _updateState.value = UpdateState.NoUpdate
                    return@withContext
                } else if (responseCode != HttpURLConnection.HTTP_OK) {
                    _updateState.value = UpdateState.NoUpdate
                    return@withContext
                }

                val responseText = connection.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(responseText)
                val tagName = json.optString("tag_name", "v1.0.0")
                val body = json.optString("body", "")
                
                val assetsJson = json.optJSONArray("assets")
                var downloadUrl = ""
                var fileName = "update.apk"
                
                if (assetsJson != null && assetsJson.length() > 0) {
                    for (i in 0 until assetsJson.length()) {
                        val asset = assetsJson.getJSONObject(i)
                        val name = asset.optString("name", "")
                        if (name.endsWith(".apk")) {
                            downloadUrl = asset.optString("browser_download_url", "")
                            fileName = name
                            break
                        }
                    }
                }

                if (downloadUrl.isEmpty()) {
                    _updateState.value = UpdateState.NoUpdate
                    return@withContext
                }

                val cleanTagName = tagName.replace(Regex("(?i)^v"), "")
                
                val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
                val currentVersionName = packageInfo.versionName ?: "1.0.0"
                val cleanCurrentVersion = currentVersionName.replace(Regex("(?i)^v"), "")

                Log.d(TAG, "Current version: $cleanCurrentVersion, Latest version: $cleanTagName")

                val isNewer = isVersionNewer(cleanCurrentVersion, cleanTagName)
                if (isNewer) {
                    _updateState.value = UpdateState.UpdateAvailable(
                        tagName = tagName,
                        body = body,
                        downloadUrl = downloadUrl,
                        fileName = fileName
                    )
                } else {
                    _updateState.value = UpdateState.NoUpdate
                }

            } catch (e: Exception) {
                Log.e(TAG, "Error checking update", e)
                _updateState.value = UpdateState.Error(e.localizedMessage ?: "Network error checking updates.")
            }
        }
    }

    private fun isVersionNewer(current: String, latest: String): Boolean {
        try {
            val currentParts = current.split(".").mapNotNull { it.toIntOrNull() }
            val latestParts = latest.split(".").mapNotNull { it.toIntOrNull() }
            
            val maxLength = maxOf(currentParts.size, latestParts.size)
            for (i in 0 until maxLength) {
                val currentVal = currentParts.getOrElse(i) { 0 }
                val latestVal = latestParts.getOrElse(i) { 0 }
                if (latestVal > currentVal) return true
                if (currentVal > latestVal) return false
            }
            return false
        } catch (e: Exception) {
            return latest != current
        }
    }

    suspend fun downloadAndInstallApk(context: Context, downloadUrl: String, fileName: String) {
        _updateState.value = UpdateState.Downloading(0f)
        
        withContext(Dispatchers.IO) {
            try {
                val url = URL(downloadUrl)
                val connection = url.openConnection() as HttpURLConnection
                connection.connectTimeout = 15000
                connection.readTimeout = 30000
                connection.connect()

                if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                    _updateState.value = UpdateState.Error("Download failed: Server returned code ${connection.responseCode}")
                    return@withContext
                }

                val fileLength = connection.contentLength
                val cacheDir = context.cacheDir
                val apkFile = File(cacheDir, fileName)
                
                if (apkFile.exists()) {
                    apkFile.delete()
                }

                val inputStream = BufferedInputStream(connection.inputStream)
                val outputStream = FileOutputStream(apkFile)

                val data = ByteArray(4096)
                var total: Long = 0
                var count: Int
                
                while (inputStream.read(data).also { count = it } != -1) {
                    total += count
                    if (fileLength > 0) {
                        _updateState.value = UpdateState.Downloading(total.toFloat() / fileLength.toFloat())
                    }
                    outputStream.write(data, 0, count)
                }

                outputStream.flush()
                outputStream.close()
                inputStream.close()

                _updateState.value = UpdateState.Success(apkFile)
                
                withContext(Dispatchers.Main) {
                    triggerInstallation(context, apkFile)
                }

            } catch (e: Exception) {
                Log.e(TAG, "Download error", e)
                _updateState.value = UpdateState.Error("Download failed: ${e.localizedMessage}")
            }
        }
    }

    fun triggerInstallation(context: Context, apkFile: File) {
        try {
            if (!apkFile.exists()) {
                _updateState.value = UpdateState.Error("Apk file not found inside cache.")
                return
            }

            val apkUri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.provider",
                apkFile
            )

            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Installation failed", e)
            _updateState.value = UpdateState.Error("Failed to open apk: ${e.localizedMessage}")
        }
    }
}
