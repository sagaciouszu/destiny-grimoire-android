package com.grimoire.android.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.grimoire.android.R

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun BookmarksScreen(
    viewModel: AppViewModel,
    onBack: () -> Unit,
    onOpenArticle: (source: String, slug: String) -> Unit,
) {
    val bookmarks by viewModel.bookmarks.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Bookmarks") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.Filled.ArrowBack,
                            contentDescription = "Back",
                            modifier = Modifier.size(24.dp),
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
    ) { padding ->
        if (bookmarks.isEmpty()) {
            EmptyBookmarksState(padding, MaterialTheme.colorScheme)
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(bookmarks.entries.toList(), key = { it.key }) { (key, title) ->
                    BookmarkRow(
                        key = key,
                        title = title,
                        onOpen = {
                            val parts = key.split(":", limit = 2)
                            if (parts.size == 2) {
                                onOpenArticle(parts[0], parts[1])
                            }
                        },
                        onRemove = {
                            val parts = key.split(":", limit = 2)
                            if (parts.size == 2) {
                                viewModel.removeBookmark(parts[0], parts[1])
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyBookmarksState(
    padding: androidx.compose.foundation.layout.PaddingValues,
    colors: androidx.compose.material3.ColorScheme,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .padding(horizontal = 24.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = Icons.Filled.BookmarkBorder,
            contentDescription = "No bookmarks",
            tint = colors.onSurfaceVariant.copy(alpha = 0.4f),
            modifier = Modifier.size(64.dp),
        )
        Spacer(Modifier.height(20.dp))
        Text(
            text = "No bookmarks yet",
            style = MaterialTheme.typography.titleMedium,
            color = colors.onSurfaceVariant,
        )
        Text(
            text = "Bookmark articles from search results to read them later.",
            style = MaterialTheme.typography.bodyMedium,
            color = colors.onSurfaceVariant.copy(alpha = 0.7f),
        )
    }
}

@Composable
private fun BookmarkRow(
    key: String,
    title: String,
    onOpen: () -> Unit,
    onRemove: () -> Unit,
) {
    val parts = key.split(":", limit = 2)
    val source = if (parts.size == 2) parts[0] else ""
    val slug = if (parts.size == 2) parts[1] else ""

    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = androidx.compose.material3.MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onOpen, role = Role.Button)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = kindLabel(source),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(
                onClick = onRemove,
            ) {
                Icon(
                    imageVector = Icons.Filled.Delete,
                    contentDescription = "Remove bookmark",
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(24.dp),
                )
            }
        }
    }
}
