package com.grimoire.android.data

import com.grimoire.android.native.GrimoireNative
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/** Wraps the native article fetcher and maps its JSON onto Kotlin models. */
object GrimoireArticleRepository {

    suspend fun load(source: String, sourceSlug: String): ArticleContent =
        withContext(Dispatchers.IO) {
            val raw = GrimoireNative.nativeArticle(source, sourceSlug)
            runCatching { parse(raw) }
                .getOrElse { ArticleContent(found = false, kind = source, title = "", subtitle = "", blocks = emptyList()) }
        }

    /** Plain-text rendering of the article, used for sharing. */
    fun toPlainText(article: ArticleContent): String = buildString {
        appendLine(article.title)
        if (article.subtitle.isNotBlank()) appendLine(article.subtitle)
        appendLine()
        for (block in article.blocks) {
            when {
                block.heading.isNotBlank() -> appendLine("\n${block.heading}\n")
                block.label.isNotBlank() -> appendLine("${block.label}: ${block.text}")
                else -> appendLine(block.text)
            }
        }
    }.trim()

    private fun parse(raw: String): ArticleContent {
        val obj = JSONObject(raw)
        if (!obj.optBoolean("found", false)) {
            return ArticleContent(found = false, kind = "", title = "", subtitle = "", blocks = emptyList())
        }
        val blocksJson = obj.optJSONArray("blocks")
        val blocks = buildList {
            if (blocksJson != null) {
                for (i in 0 until blocksJson.length()) {
                    val b = blocksJson.getJSONObject(i)
                    add(
                        ArticleBlock(
                            label = b.optString("label"),
                            heading = b.optString("heading"),
                            text = b.optString("text"),
                            stage = b.optBoolean("stage", false),
                        )
                    )
                }
            }
        }
        return ArticleContent(
            found = true,
            kind = obj.optString("kind"),
            title = obj.optString("title"),
            subtitle = obj.optString("subtitle"),
            blocks = blocks,
        )
    }
}
