package com.grimoire.android.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.foundation.layout.size
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.grimoire.android.R
import com.grimoire.android.data.SearchResult
import com.grimoire.android.data.SourceKind

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    viewModel: AppViewModel,
    onOpenArticle: (source: String, slug: String) -> Unit,
    onOpenSettings: () -> Unit,
) {
    val state by viewModel.search.collectAsState()

    Column(Modifier.fillMaxSize()) {
        // Header bar with the scroll-text glyph + app name
        TopAppBar(
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        painter = painterResource(R.drawable.lucide_ic_scroll_text),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.widthIn(12.dp))
                    Text(
                        "Grimoire",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            },
            actions = {
                IconButton(onClick = onOpenSettings) {
                    Icon(
                        painter = painterResource(R.drawable.lucide_ic_settings),
                        contentDescription = "Settings",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.surface,
            ),
        )

        // Search field
        OutlinedTextField(
            value = state.query,
            onValueChange = viewModel::onQueryChange,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            placeholder = { Text("Search the grimoire…") },
            leadingIcon = {
                Icon(
                    painter = painterResource(R.drawable.lucide_ic_search),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            trailingIcon = {
                if (state.query.isNotEmpty()) {
                    IconButton(onClick = { viewModel.onQueryChange("") }) {
                        Icon(
                            painter = painterResource(R.drawable.lucide_ic_x),
                            contentDescription = "Clear search",
                        )
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(50.dp),
            colors = OutlinedTextFieldDefaults.colors(),
        )

        // Progress bar while searching
        if (state.loading) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
        } else {
            Spacer(Modifier.height(4.dp))
        }

        // Source filter chips
        LazyRowSourceFilter(
            selected = state.source,
            onSelect = viewModel::onSourceChange,
        )

        // Result meta row
        if (state.response.results.isNotEmpty()) {
            Text(
                text = "${formatNumber(state.response.total)} results · ${state.response.elapsedMs.toInt()} ms",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }

        // Results
        when {
            state.query.isBlank() -> PromptIdleState()
            state.response.results.isEmpty() && !state.loading -> EmptyResultsState(state.query)
            else -> ResultsList(results = state.response.results, onOpenArticle = onOpenArticle)
        }
    }
}

@Composable
private fun LazyRowSourceFilter(
    selected: SourceKind,
    onSelect: (SourceKind) -> Unit,
) {
    androidx.compose.foundation.lazy.LazyRow(
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(SourceKind.entries.toList()) { kind ->
            FilterChip(
                selected = kind == selected,
                onClick = { onSelect(kind) },
                label = { Text(kind.label) },
                leadingIcon = {
                    Icon(
                        painter = painterResource(iconForSource(kind)),
                        contentDescription = null,
                        modifier = Modifier.size(FilterChipDefaults.IconSize),
                    )
                },
            )
        }
    }
}

@Composable
private fun ResultsList(
    results: List<SearchResult>,
    onOpenArticle: (String, String) -> Unit,
) {
    LazyColumn(
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        items(results, key = { "${it.source}:${it.sourceSlug}:${it.title}:${it.snippet.hashCode()}" }) { result ->
            ResultCard(result, onOpenArticle = { onOpenArticle(result.source, result.sourceSlug) })
        }
    }
}

@Composable
private fun ResultCard(result: SearchResult, onOpenArticle: () -> Unit) {
    val context = LocalContext.current
    val gold = MaterialTheme.colorScheme.primary
    val rawSnippet = result.snippet
    val snippetText = remember(rawSnippet) {
        buildHighlighted(rawSnippet, gold)
    }

    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpenArticle, role = Role.Button),
        colors = CardDefaults.elevatedCardColors(),
    ) {
        Column(Modifier.padding(14.dp)) {
            // Title row + copy button
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
            ) {
                Text(
                    text = result.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 8.dp),
                    maxLines = 3,
                )
                IconButton(
                    onClick = { copyToClipboard(context, rawSnippet.replace("[", "").replace("]", "")) },
                ) {
                    Icon(
                        painter = painterResource(R.drawable.lucide_ic_copy),
                        contentDescription = "Copy snippet",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // Speaker badge, when present
            if (result.speaker.isNotBlank()) {
                Text(
                    text = result.speaker,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }

            // Snippet with highlighted matches
            if (rawSnippet.isNotBlank()) {
                Text(
                    text = snippetText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }

            // Source kind chip
            Row(Modifier.padding(top = 10.dp)) {
                MetaChip(kindOf(result.source))
            }
        }
    }
}

@Composable
private fun MetaChip(label: String) {
    androidx.compose.material3.Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(6.dp),
    ) {
        Text(
            text = label.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}

@Composable
private fun PromptIdleState() {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            painter = painterResource(R.drawable.lucide_ic_book_open_text),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
            modifier = Modifier.fillMaxSize(0.18f),
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = "Search 69,000+ lines of canon",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = "Books, transcripts, items, interactions and triumphs — all offline.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
        )
    }
}

@Composable
private fun EmptyResultsState(query: String) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            painter = painterResource(R.drawable.lucide_ic_search),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
            modifier = Modifier.fillMaxSize(0.14f),
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = "No matches for “$query”",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = "Try different words — or an exact phrase like \"the Witness\".",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun iconForSource(kind: SourceKind): Int = when (kind) {
    SourceKind.ALL -> R.drawable.lucide_ic_layout_grid
    SourceKind.TRANSCRIPT -> R.drawable.lucide_ic_scroll_text
    SourceKind.ITEM -> R.drawable.lucide_ic_sword
    SourceKind.LORE -> R.drawable.lucide_ic_book_text
    SourceKind.GRIMOIRE_CARD -> R.drawable.lucide_ic_sparkles
    SourceKind.BOOK -> R.drawable.lucide_ic_book_open
    SourceKind.INTERACTION -> R.drawable.lucide_ic_message_circle
    SourceKind.RECORD -> R.drawable.lucide_ic_trophy
}

private fun kindOf(source: String): String = when (source) {
    SourceKind.TRANSCRIPT.id -> "Transcript"
    SourceKind.ITEM.id -> "Item"
    SourceKind.LORE.id -> "Lore"
    SourceKind.GRIMOIRE_CARD.id -> "Grimoire"
    SourceKind.BOOK.id -> "Book"
    SourceKind.INTERACTION.id -> "Interaction"
    SourceKind.RECORD.id -> "Triumph"
    else -> source
}

private fun formatNumber(n: Int): String = "%,d".format(n)

private fun buildHighlighted(snippet: String, gold: androidx.compose.ui.graphics.Color): AnnotatedString =
    buildAnnotatedString {
        var rest = snippet
        while (true) {
            val start = rest.indexOf('[')
            if (start == -1) { append(rest); break }
            append(rest, 0, start)
            val end = rest.indexOf(']', start)
            if (end == -1) { append(rest.substring(start)); break }
            withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = gold)) {
                append(rest, start + 1, end)
            }
            rest = rest.substring(end + 1)
        }
    }

private fun copyToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("grimoire snippet", text))
}
