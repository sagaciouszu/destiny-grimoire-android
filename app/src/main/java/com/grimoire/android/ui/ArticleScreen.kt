package com.grimoire.android.ui

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.grimoire.android.R
import com.grimoire.android.data.ArticleContent
import com.grimoire.android.data.GrimoireArticleRepository
import com.grimoire.android.data.LineSpacing
import com.grimoire.android.data.ReadingSize

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArticleScreen(
    source: String,
    sourceSlug: String,
    viewModel: AppViewModel,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val settings by viewModel.settings.collectAsState()
    var article by remember(source, sourceSlug) { mutableStateOf<ArticleContent?>(null) }
    var showReadingSheet by remember { mutableStateOf(false) }

    LaunchedEffect(source, sourceSlug) {
        article = GrimoireArticleRepository.load(source, sourceSlug)
    }

    val fs = settings.fontScale
    val lh = settings.lineHeightFactor

    val titleStyle = MaterialTheme.typography.headlineSmall.copy(
        fontSize = 23.sp * fs,
        lineHeight = 23.sp * fs * lh,
    )
    val bodyStyle = MaterialTheme.typography.bodyLarge.copy(
        fontSize = 16.sp * fs,
        lineHeight = 16.sp * fs * lh,
    )
    val headingStyle = MaterialTheme.typography.titleMedium.copy(
        fontSize = 17.sp * fs,
        lineHeight = 17.sp * fs * lh,
    )
    val speakerStyle = MaterialTheme.typography.labelLarge.copy(
        fontSize = 11.sp * fs,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.5.sp,
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(article?.title ?: kindLabel(source)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            painter = painterResource(R.drawable.lucide_ic_arrow_left),
                            contentDescription = "Back",
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { showReadingSheet = true }) {
                        Icon(
                            painter = painterResource(R.drawable.lucide_ic_type),
                            contentDescription = "Reading settings",
                        )
                    }
                    IconButton(onClick = { article?.let { shareArticle(context, it) } }) {
                        Icon(
                            painter = painterResource(R.drawable.lucide_ic_share_2),
                            contentDescription = "Share article",
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(),
            )
        },
    ) { padding ->
        when (val a = article) {
            null -> LoadingState(padding, MaterialTheme.colorScheme)
            else -> if (!a.found) {
                NotFoundState(padding, MaterialTheme.colorScheme, onBack)
            } else {
                ArticleBody(a, padding, titleStyle, bodyStyle, headingStyle, speakerStyle)
            }
        }
    }

    if (showReadingSheet) {
        ReadingSettingsSheet(
            viewModel = viewModel,
            onDismiss = { showReadingSheet = false },
        )
    }
}

@Composable
private fun LoadingState(
    padding: androidx.compose.foundation.layout.PaddingValues,
    colors: androidx.compose.material3.ColorScheme,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        LinearProgressIndicator()
        Spacer(Modifier.height(12.dp))
        Text("Opening the article…", color = colors.onSurfaceVariant)
    }
}

@Composable
private fun NotFoundState(
    padding: androidx.compose.foundation.layout.PaddingValues,
    colors: androidx.compose.material3.ColorScheme,
    onBack: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            painter = painterResource(R.drawable.lucide_ic_file_text),
            contentDescription = null,
            tint = colors.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            "That article could not be found in the current archive.",
            style = MaterialTheme.typography.bodyLarge,
            color = colors.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = onBack) { Text("Back to search") }
    }
}

@Composable
private fun ArticleBody(
    article: ArticleContent,
    padding: androidx.compose.foundation.layout.PaddingValues,
    titleStyle: androidx.compose.ui.text.TextStyle,
    bodyStyle: androidx.compose.ui.text.TextStyle,
    headingStyle: androidx.compose.ui.text.TextStyle,
    speakerStyle: androidx.compose.ui.text.TextStyle,
) {
    val scrollState = rememberScrollState()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .verticalScroll(scrollState)
            .padding(horizontal = 20.dp, vertical = 8.dp),
    ) {
        // Kind badge — tinted per Destiny rarity through Material 3 roles
        Surface(
            color = badgeBackground(article.kind),
            shape = MaterialTheme.shapes.small,
        ) {
            Text(
                text = kindLabel(article.kind).uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = badgeForeground(article.kind),
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }

        Spacer(Modifier.height(10.dp))
        Text(
            text = article.title,
            style = titleStyle,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(end = 8.dp),
        )
        if (article.subtitle.isNotBlank()) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = article.subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(12.dp))
        androidx.compose.material3.HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))

        // Blocks are selectable so quotes can be copied straight out
        androidx.compose.foundation.text.selection.SelectionContainer {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                for (block in article.blocks) {
                    when {
                        block.heading.isNotBlank() -> HeadingBlock(block.heading, headingStyle)
                        block.stage -> StageBlock(block.text, bodyStyle)
                        block.label.isNotBlank() -> SpeakerBlock(block.label, block.text, speakerStyle, bodyStyle)
                        else -> PlainBlock(block.text, bodyStyle)
                    }
                }
            }
        }

        Spacer(Modifier.height(24.dp))
        Text(
            text = "Source: The Ishtar Collective · Destinypedia · reviewed captions.\nDestiny is a trademark of Bungie Inc.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
        )
        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun HeadingBlock(text: String, style: androidx.compose.ui.text.TextStyle) {
    Text(
        text = text,
        style = style,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.padding(top = 6.dp),
    )
}

@Composable
private fun StageBlock(text: String, style: androidx.compose.ui.text.TextStyle) {
    Text(
        text = text,
        style = style.copy(fontStyle = FontStyle.Italic),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun SpeakerBlock(
    speaker: String,
    text: String,
    speakerStyle: androidx.compose.ui.text.TextStyle,
    bodyStyle: androidx.compose.ui.text.TextStyle,
) {
    Column {
        Text(
            text = speaker.uppercase(),
            style = speakerStyle,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = text,
            style = bodyStyle,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun PlainBlock(text: String, style: androidx.compose.ui.text.TextStyle) {
    Text(
        text = text,
        style = style,
        color = MaterialTheme.colorScheme.onSurface,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReadingSettingsSheet(
    viewModel: AppViewModel,
    onDismiss: () -> Unit,
) {
    val settings by viewModel.settings.collectAsState()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 4.dp)
                .padding(bottom = 24.dp),
        ) {
            Text(
                text = "Reading settings",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )

            Spacer(Modifier.height(12.dp))
            Text(
                "Font size",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            for (size in ReadingSize.entries) {
                RadioOptionRow(
                    label = size.label,
                    selected = settings.readingSize == size,
                    onSelect = { viewModel.onReadingSizeChange(size) },
                ) {
                    Text(
                        text = "Aa",
                        style = MaterialTheme.typography.bodyLarge.copy(fontSize = 14.sp * size.scale),
                        fontWeight = FontWeight.Medium,
                    )
                }
            }

            Spacer(Modifier.height(12.dp))
            Text(
                "Line spacing",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            for (spacing in LineSpacing.entries) {
                RadioOptionRow(
                    label = spacing.label,
                    selected = settings.lineSpacing == spacing,
                    onSelect = { viewModel.onLineSpacingChange(spacing) },
                )
            }

            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun RadioOptionRow(
    label: String,
    selected: Boolean,
    onSelect: () -> Unit,
    trailing: @Composable (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            RadioButton(selected = selected, onClick = onSelect)
            Spacer(Modifier.width(4.dp))
            Text(label, style = MaterialTheme.typography.bodyLarge)
        }
        trailing?.invoke()
    }
}

private fun shareArticle(context: Context, article: ArticleContent) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, article.title)
        putExtra(Intent.EXTRA_TEXT, GrimoireArticleRepository.toPlainText(article))
    }
    context.startActivity(Intent.createChooser(intent, null))
}

@Composable
private fun badgeBackground(kind: String): androidx.compose.ui.graphics.Color =
    when (kind) {
        "transcript_line", "transcript" -> MaterialTheme.colorScheme.tertiaryContainer
        "item" -> MaterialTheme.colorScheme.secondaryContainer
        "lore_entry", "record" -> MaterialTheme.colorScheme.primaryContainer
        else -> MaterialTheme.colorScheme.surfaceVariant
    }

@Composable
private fun badgeForeground(kind: String): androidx.compose.ui.graphics.Color =
    when (kind) {
        "transcript_line", "transcript" -> MaterialTheme.colorScheme.onTertiaryContainer
        "item" -> MaterialTheme.colorScheme.onSecondaryContainer
        "lore_entry", "record" -> MaterialTheme.colorScheme.onPrimaryContainer
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

fun kindLabel(source: String): String = when (source) {
    "transcript_line", "transcript" -> "Transcript"
    "item" -> "Item"
    "lore_entry" -> "Lore"
    "grimoire_card" -> "Grimoire card"
    "book_entry", "book" -> "Book"
    "interaction" -> "Interaction"
    "record" -> "Triumph"
    else -> source
}
