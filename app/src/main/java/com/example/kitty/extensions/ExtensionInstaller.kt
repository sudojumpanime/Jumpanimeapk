package com.example.kitty.extensions

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ExtensionInstaller(
    private val repository: ExtensionRepository,
) {
    /**
     * Paste a GitHub repo / tree / blob URL (or `owner/repo`) and install the
     * first reachable `anikitty-extension.json`.
     */
    suspend fun installFromGithub(input: String): ExtensionManifest = withContext(Dispatchers.IO) {
        val target = GitHubExtensionLocator.parse(input)
        var lastError: String? = null
        for (rawUrl in target.rawCandidates) {
            val result = runCatching {
                val body = ExtensionHttp.getString(rawUrl)
                val manifest = ExtensionManifestParser.parse(
                    raw = body,
                    sourceGithub = target.githubWebUrl,
                    manifestUrl = rawUrl,
                )
                repository.upsert(manifest)
                manifest
            }
            if (result.isSuccess) return@withContext result.getOrThrow()
            lastError = result.exceptionOrNull()?.message
        }
        val hint = lastError.orEmpty()
        when {
            hint.contains("iconUrl", ignoreCase = true) ->
                error("$hint — add the scraped site’s favicon as iconUrl in anikitty-extension.json")
            hint.isNotBlank() ->
                error(hint)
            else ->
                error("anikitty-extension.json not found on that GitHub repo")
        }
    }

    suspend fun installFromManifestUrl(
        manifestUrl: String,
        github: String? = null,
    ): ExtensionManifest = withContext(Dispatchers.IO) {
        val body = ExtensionHttp.getString(manifestUrl)
        val manifest = ExtensionManifestParser.parse(
            raw = body,
            sourceGithub = github,
            manifestUrl = manifestUrl,
        )
        repository.upsert(manifest)
        manifest
    }
}
