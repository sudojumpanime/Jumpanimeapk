package com.example.kitty.extensions

/**
 * Turns a pasted GitHub link (or `owner/repo`) into raw.githubusercontent.com
 * URLs for `anikitty-extension.json`.
 */
object GitHubExtensionLocator {
    data class Target(
        val owner: String,
        val repo: String,
        val ref: String,
        val subdir: String,
        val githubWebUrl: String,
        val rawCandidates: List<String>,
    )

    fun parse(input: String): Target {
        val trimmed = input.trim().removeSuffix(".git")
        require(trimmed.isNotEmpty()) { "Empty GitHub URL" }

        // owner/repo
        val short = Regex("""^([A-Za-z0-9_.-]+)/([A-Za-z0-9_.-]+)$""").matchEntire(trimmed)
        if (short != null) {
            return build(short.groupValues[1], short.groupValues[2], "main", "")
        }

        val url = trimmed
            .removePrefix("https://")
            .removePrefix("http://")
            .removePrefix("www.")
        require(url.startsWith("github.com/", ignoreCase = true)) {
            "Paste a GitHub repo URL (github.com/owner/repo)"
        }
        val path = url.removePrefix("github.com/").removePrefix("Github.com/")
        val parts = path.split('/').filter { it.isNotEmpty() }
        require(parts.size >= 2) { "Need github.com/owner/repo" }
        val owner = parts[0]
        val repo = parts[1].removeSuffix(".git")

        var ref = "main"
        var subdir = ""
        when {
            parts.size >= 4 && parts[2] == "tree" -> {
                ref = parts[3]
                if (parts.size > 4) subdir = parts.drop(4).joinToString("/")
            }
            parts.size >= 4 && parts[2] == "blob" -> {
                ref = parts[3]
                if (parts.size > 4) {
                    val rest = parts.drop(4)
                    // drop filename if last segment is the manifest
                    subdir = if (rest.lastOrNull() == "anikitty-extension.json") {
                        rest.dropLast(1).joinToString("/")
                    } else {
                        rest.joinToString("/")
                    }
                }
            }
        }

        return build(owner, repo, ref, subdir)
    }

    private fun build(owner: String, repo: String, ref: String, subdir: String): Target {
        val cleanSub = subdir.trim('/')
        val prefix = if (cleanSub.isEmpty()) "" else "$cleanSub/"
        val refs = linkedSetOf(ref, "main", "master").toList()
        val raws = refs.map { r ->
            "https://raw.githubusercontent.com/$owner/$repo/$r/${prefix}anikitty-extension.json"
        }.distinct()
        val web = if (cleanSub.isEmpty()) {
            "https://github.com/$owner/$repo"
        } else {
            "https://github.com/$owner/$repo/tree/$ref/$cleanSub"
        }
        return Target(
            owner = owner,
            repo = repo,
            ref = ref,
            subdir = cleanSub,
            githubWebUrl = web,
            rawCandidates = raws,
        )
    }
}
