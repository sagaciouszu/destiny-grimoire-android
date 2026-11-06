package com.grimoire.android.data

import com.grimoire.android.native.GrimoireNative
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/** Wraps the native FTS5 search engine and maps its JSON onto Kotlin models. */
object GrimoireSearchRepository {

    suspend fun search(
        query: String,
        source: SourceKind = SourceKind.ALL,
        limit: Int = 25,
        offset: Int = 0,
    ): SearchResponse = withContext(Dispatchers.IO) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return@withContext SearchResponse()

        val raw = GrimoireNative.nativeSearch(trimmed, source.id, limit, offset)
        runCatching { parseResponse(raw) }
            .getOrElse { SearchResponse(error = "Native search failed: ${it.message}") }
    }

    private fun parseResponse(raw: String): SearchResponse {
        val obj = JSONObject(raw)
        val error = obj.optString("error", "").takeIf { it.isNotEmpty() }
        val resultsJson = obj.optJSONArray("results")
        val results = buildList {
            if (resultsJson != null) {
                for (i in 0 until resultsJson.length()) {
                    val r = resultsJson.getJSONObject(i)
                    add(
                        SearchResult(
                            title = r.optString("title"),
                            speaker = r.optString("speaker"),
                            source = r.optString("source"),
                            sourceSlug = r.optString("source_slug"),
                            snippet = r.optString("snippet"),
                        )
                    )
                }
            }
        }
        return SearchResponse(
            results = results,
            total = obj.optInt("total"),
            elapsedMs = obj.optDouble("elapsedMs"),
            error = error,
        )
    }
}
