package com.example.kitty.extensions

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.json.JSONArray

/**
 * Persists user-installed extension manifests (BYO stream servers).
 */
class ExtensionRepository(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val _installed = MutableStateFlow(loadAll())
    val installed: StateFlow<List<ExtensionManifest>> = _installed.asStateFlow()

    fun get(id: String): ExtensionManifest? = _installed.value.firstOrNull { it.id == id }

    fun upsert(manifest: ExtensionManifest) {
        val next = _installed.value.filterNot { it.id == manifest.id } + manifest
        persist(next)
        _installed.update { next.sortedBy { it.name.lowercase() } }
    }

    fun remove(id: String) {
        val next = _installed.value.filterNot { it.id == id }
        persist(next)
        _installed.update { next }
    }

    private fun loadAll(): List<ExtensionManifest> {
        val raw = prefs.getString(KEY_LIST, null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    add(ExtensionManifestParser.parse(arr.getString(i)))
                }
            }.sortedBy { it.name.lowercase() }
        }.getOrDefault(emptyList())
    }

    private fun persist(list: List<ExtensionManifest>) {
        val arr = JSONArray()
        list.forEach { arr.put(ExtensionManifestParser.toJson(it)) }
        prefs.edit().putString(KEY_LIST, arr.toString()).apply()
    }

    private companion object {
        const val PREFS = "anikitty_extensions"
        const val KEY_LIST = "installed_json"
    }
}
