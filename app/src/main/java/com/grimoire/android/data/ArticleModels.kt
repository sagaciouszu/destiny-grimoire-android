package com.grimoire.android.data

/** One block of an article: a labeled speaker line, a section heading, or a plain paragraph. */
data class ArticleBlock(
    val label: String,
    val heading: String,
    val text: String,
    val stage: Boolean,
)

data class ArticleContent(
    val found: Boolean,
    val kind: String,
    val title: String,
    val subtitle: String,
    val blocks: List<ArticleBlock>,
)
