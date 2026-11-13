package com.example.kitty.extensions

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

class ExtensionStoreClient(
    private val indexUrls: List<String> = DEFAULT_INDEX_URLS,
) {
    suspend fun fetchIndex(): StoreIndex = withContext(Dispatchers.IO) {
        var last: String? = null
        for (url in indexUrls) {
            val result = runCatching {
                parseIndex(ExtensionHttp.getString(url), sourceUrl = url)
            }
            if (result.isSuccess) return@withContext result.getOrThrow()
            last = result.exceptionOrNull()?.message
        }
        error(last ?: "Could not load extension store")
    }

    private fun parseIndex(raw: String, sourceUrl: String): StoreIndex {
        val o = JSONObject(raw)
        val arr = o.getJSONArray("extensions")
        val list = buildList {
            for (i in 0 until arr.length()) {
                val e = arr.getJSONObject(i)
                add(
                    StoreListing(
                        id = e.getString("id"),
                        name = e.getString("name"),
                        description = e.optString("description", ""),
                        github = e.getString("github"),
                        author = e.optString("author", ""),
                        featured = e.optBoolean("featured", false),
                        manifestPath = e.optString("manifestPath")
                            .takeIf { it.isNotBlank() },
                        iconUrl = e.optString("iconUrl").takeIf { it.isNotBlank() },
                        siteUrl = e.optString("siteUrl").takeIf { it.isNotBlank() },
                    ),
                )
            }
        }
        return StoreIndex(
            apiVersion = o.optInt("apiVersion", 1),
            updatedAt = o.optString("updatedAt", null)?.takeIf { it.isNotBlank() },
            extensions = list,
            sourceUrl = sourceUrl,
        )
    }

    companion object {
        /**
         * Prefer the monorepo index (always published with APK/provider changes),
         * then the dedicated `anikitty-extensions` mirror. Dedicated can lag when
         * only fampep can push that repo — monorepo-first keeps Gotaku/Anikoto current.
         */
        val DEFAULT_INDEX_URLS = listOf(
            "https://raw.githubusercontent.com/fampep/anikitty/main/extensions/store/index.json",
            "https://raw.githubusercontent.com/fampep/anikitty-extensions/main/store/index.json",
        )

        /**
         * Main store submission — opens GitHub issue form on the extensions repo
         * (falls back to anikitty issues until that public repo exists).
         */
        const val SUBMIT_URL =
            "https://github.com/fampep/anikitty-extensions/issues/new?template=extension_submit.yml"

        const val SUBMIT_FALLBACK_URL =
            "https://github.com/fampep/anikitty/issues/new?template=extension_submit.yml"
    }
}
