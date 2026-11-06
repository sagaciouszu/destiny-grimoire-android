package com.grimoire.android.data

/** Searchable content categories, mapped to the `source` column in the FTS index. */
enum class SourceKind(val id: String, val label: String) {
    ALL("", "All"),
    TRANSCRIPT("transcript_line", "Transcripts"),
    ITEM("item", "Items"),
    LORE("lore_entry", "Lore"),
    GRIMOIRE_CARD("grimoire_card", "Grimoire"),
    BOOK("book_entry", "Books"),
    INTERACTION("interaction", "Interactions"),
    RECORD("record", "Triumphs");

    companion object {
        fun fromId(id: String): SourceKind =
            entries.firstOrNull { it.id == id } ?: ALL
    }
}

data class SearchResult(
    val title: String,
    val speaker: String,
    val source: String,
    val sourceSlug: String,
    val snippet: String,
) {
    val kind: SourceKind get() = SourceKind.fromId(source)
    val isTranscriptLine: Boolean get() = source == SourceKind.TRANSCRIPT.id
    val isItem: Boolean get() = source == SourceKind.ITEM.id
}

data class SearchResponse(
    val results: List<SearchResult> = emptyList(),
    val total: Int = 0,
    val elapsedMs: Double = 0.0,
    val error: String? = null,
)
