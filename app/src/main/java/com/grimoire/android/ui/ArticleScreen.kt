package com.grimoire.android.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.speech.tts.TextToSpeech
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.grimoire.android.R
import com.grimoire.android.data.ArticleContent
import com.grimoire.android.data.FontFamily
import com.grimoire.android.data.GrimoireArticleRepository
import com.grimoire.android.data.LineSpacing
import com.grimoire.android.data.ReadingSize
import com.grimoire.android.data.ReadingTheme
import com.grimoire.android.ui.theme.ReadingFontStyle
import com.grimoire.android.ui.theme.ReadingThemeOverlay

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
    val readingProgress by viewModel.readingProgress.collectAsState()
    var article by remember(source, sourceSlug) { mutableStateOf<ArticleContent?>(null) }
    var showReadingSheet by remember { mutableStateOf(false) }
    var isBookmarked by remember { mutableStateOf(false) }
    var isPlaying by remember { mutableStateOf(false) }
    var scrollPosition by remember { mutableFloatStateOf(0.0f) }

    val tts = remember { TextToSpeech(context) {} }

    val key = "$source:$sourceSlug"

    LaunchedEffect(source, sourceSlug) {
        article = GrimoireArticleRepository.load(source, sourceSlug)
        isBookmarked = viewModel.isBookmarked(source, sourceSlug)
        // Restore saved reading progress
        scrollPosition = viewModel.getReadingProgress(source, sourceSlug)
    }

    LaunchedEffect(tts) {
        tts.setOnUtteranceCompletedListener { _ ->
            isPlaying = false
        }
    }

    // Persist scroll position when it changes
    LaunchedEffect(scrollPosition) {
        if (article != null && article?.found == true) {
            viewModel.saveReadingProgress(source, sourceSlug, scrollPosition)
        }
    }

    val fs = settings.fontScale
    val lh = settings.lineHeightFactor

    // --- Typography, scaled by reading settings + font family ---
    val baseSize = 16.sp
    val titleExtra = 22.sp
    val largeSize = 17.sp
    val smallSize = 11.sp

    val titleStyle = ReadingFontStyle(
        settings.fontFamily,
        TextStyle(
            fontSize = titleExtra * fs,
            lineHeight = titleExtra * fs * lh,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.5.sp,
        ),
    )

    val bodyStyle = ReadingFontStyle(
        settings.fontFamily,
        TextStyle(
            fontSize = baseSize * fs,
            lineHeight = baseSize * fs * lh,
            fontWeight = FontWeight.Normal,
        ),
    )

    val headingStyle = ReadingFontStyle(
        settings.fontFamily,
        TextStyle(
            fontSize = largeSize * fs,
            lineHeight = largeSize * fs * lh,
            fontWeight = FontWeight.SemiBold,
        ),
    )

    val speakerStyle = ReadingFontStyle(
        settings.fontFamily,
        TextStyle(
            fontSize = smallSize * fs,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.75.sp,
        ),
    )

    // Reading time estimate
    val wordCount = article?.blocks?.sumOf { it.text.length / 6 } ?: 0
    val readingTimeMinutes = (wordCount / 200).coerceAtLeast(1)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    val a = article
                    if (a != null && a.found) {
                        Text(
                            text = a.title,
                            style = titleStyle,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 2,
                        )
                    } else {
                        Text(text = kindLabel(source), style = MaterialTheme.typography.bodyMedium)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            painter = painterResource(R.drawable.lucide_ic_arrow_left),
                            contentDescription = "Back",
                            modifier = Modifier.size(24.dp),
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            if (isBookmarked) {
                                viewModel.removeBookmark(source, sourceSlug)
                                isBookmarked = false
                            } else {
                                viewModel.addBookmark(source, sourceSlug, article?.title ?: "")
                                isBookmarked = true
                            }
                        },
                    ) {
                        Icon(
                            painter = painterResource(
                                if (isBookmarked) R.drawable.lucide_ic_bookmark_check
                                else R.drawable.lucide_ic_bookmark,
                            ),
                            contentDescription = if (isBookmarked) "Remove bookmark" else "Bookmark article",
                            tint = if (isBookmarked) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(24.dp),
                        )
                    }
                    IconButton(onClick = { showReadingSheet = true }) {
                        Icon(
                            painter = painterResource(R.drawable.lucide_ic_type),
                            contentDescription = "Reading settings",
                            modifier = Modifier.size(24.dp),
                        )
                    }
                    IconButton(
                        onClick = {
                            if (isPlaying) {
                                tts.stop()
                                isPlaying = false
                            } else {
                                article?.let { speakArticle(context, tts, it) }
                                isPlaying = true
                            }
                        },
                    ) {
                        Icon(
                            painter = painterResource(
                                if (isPlaying) R.drawable.lucide_ic_volume_x
                                else R.drawable.lucide_ic_speaker,
                            ),
                            contentDescription = if (isPlaying) "Stop reading aloud" else "Read aloud",
                            modifier = Modifier.size(24.dp),
                        )
                    }
                    IconButton(onClick = { article?.let { shareArticle(context, it) } }) {
                        Icon(
                            painter = painterResource(R.drawable.lucide_ic_share_2),
                            contentDescription = "Share article",
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
        when (val a = article) {
            null -> LoadingState(padding, MaterialTheme.colorScheme)
            else -> if (!a.found) {
                NotFoundState(padding, MaterialTheme.colorScheme, onBack)
            } else {
                ArticleBody(
                    a,
                    padding,
                    titleStyle,
                    bodyStyle,
                    headingStyle,
                    speakerStyle,
                    settings.readingTheme,
                    settings.fontFamily,
                    settings.fontScale,
                    settings.lineHeightFactor,
                    scrollPosition = { pos -> scrollPosition = pos },
                    readingTimeMinutes = readingTimeMinutes,
                )
            }
        }
    }

    if (showReadingSheet) {
        ReadingSettingsSheet(
            viewModel = viewModel,
            currentTheme = settings.readingTheme,
            currentFont = settings.fontFamily,
            currentSize = settings.readingSize,
            currentSpacing = settings.lineSpacing,
            onThemeChange = { viewModel.onReadingThemeChange(it) },
            onFontChange = { viewModel.onFontFamilyChange(it) },
            onSizeChange = { viewModel.onReadingSizeChange(it) },
            onSpacingChange = { viewModel.onLineSpacingChange(it) },
            onDismiss = { showReadingSheet = false },
        )
    }

    LaunchedEffect(Unit) {
        tts.shutdown()
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
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CircularProgressIndicator()
        Spacer(Modifier.height(16.dp))
        Text(
            text = "Opening the article…",
            style = MaterialTheme.typography.bodyMedium,
            color = colors.onSurfaceVariant,
        )
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
            .padding(horizontal = 24.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            painter = painterResource(R.drawable.lucide_ic_file_text),
            contentDescription = "Article not found",
            tint = colors.onSurfaceVariant,
            modifier = Modifier.size(48.dp),
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = "That article could not be found in the current archive.",
            style = MaterialTheme.typography.bodyLarge,
            color = colors.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(16.dp))
        TextButton(
            onClick = onBack,
        ) {
            Text("Back to search")
        }
    }
}

@Composable
private fun ArticleBody(
    article: ArticleContent,
    padding: androidx.compose.foundation.layout.PaddingValues,
    titleStyle: TextStyle,
    bodyStyle: TextStyle,
    headingStyle: TextStyle,
    speakerStyle: TextStyle,
    readingTheme: ReadingTheme,
    fontFamily: FontFamily,
    fontScale: Float,
    lineHeightFactor: Float,
    scrollPosition: (Float) -> Unit,
    readingTimeMinutes: Int,
) {
    val scrollState = rememberScrollState()

    // Report scroll position as user reads
    LaunchedEffect(scrollState.value) {
        val maxScroll = scrollState.maxValue.toFloat()
        val position = if (maxScroll > 0) scrollState.value / maxScroll else 0f
        scrollPosition(position)
    }

    ReadingThemeOverlay(readingTheme) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(scrollState)
                .padding(horizontal = 20.dp, vertical = 8.dp),
        ) {
            // Kind badge
            Surface(
                color = badgeBackground(article.kind),
                shape = MaterialTheme.shapes.small,
            ) {
                Text(
                    text = article.kind.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = badgeForeground(article.kind),
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }

            Spacer(Modifier.height(12.dp))
            Text(
                text = article.title,
                style = titleStyle,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(end = 8.dp),
            )
            if (article.subtitle.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = article.subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            // Reading time estimate
            Spacer(Modifier.height(4.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 4.dp),
            ) {
                Icon(
                    painter = painterResource(R.drawable.lucide_ic_clock),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = "~$readingTimeMinutes min read",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(16.dp))
            androidx.compose.material3.HorizontalDivider(
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f),
            )

            // Blocks are selectable so quotes can be copied straight out
            SelectionContainer {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
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

            Spacer(Modifier.height(32.dp))
            Text(
                text = "Source: The Ishtar Collective · Destinypedia · reviewed captions.\nDestiny is a trademark of Bungie Inc.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(40.dp))
        }
    }
}

@Composable
private fun HeadingBlock(text: String, style: TextStyle) {
    Text(
        text = text,
        style = style,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.padding(top = 8.dp),
    )
}

@Composable
private fun StageBlock(text: String, style: TextStyle) {
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
    speakerStyle: TextStyle,
    bodyStyle: TextStyle,
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
private fun PlainBlock(text: String, style: TextStyle) {
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
    currentTheme: ReadingTheme,
    currentFont: FontFamily,
    currentSize: ReadingSize,
    currentSpacing: LineSpacing,
    onThemeChange: (ReadingTheme) -> Unit,
    onFontChange: (FontFamily) -> Unit,
    onSizeChange: (ReadingSize) -> Unit,
    onSpacingChange: (LineSpacing) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
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

            Spacer(Modifier.height(16.dp))

            // Reading theme
            Text(
                text = "Reading theme",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            for (theme in ReadingTheme.entries) {
                ReadingThemeRow(
                    label = theme.label,
                    selected = currentTheme == theme,
                    onSelect = { onThemeChange(theme) },
                    previewColor = when (theme) {
                        ReadingTheme.DEFAULT -> MaterialTheme.colorScheme.background
                        ReadingTheme.SEPIA -> androidx.compose.ui.graphics.Color(0xFFF5F0E6)
                        ReadingTheme.AMBER -> androidx.compose.ui.graphics.Color(0xFF1A140A)
                    },
                )
            }

            Spacer(Modifier.height(16.dp))

            // Font family
            Text(
                text = "Font family",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            for (font in FontFamily.entries) {
                FontRow(
                    label = font.label,
                    selected = currentFont == font,
                    onSelect = { onFontChange(font) },
                    fontFamily = font,
                )
            }

            Spacer(Modifier.height(16.dp))

            // Font size
            Text(
                text = "Font size",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            for (size in ReadingSize.entries) {
                RadioOptionRow(
                    label = size.label,
                    selected = currentSize == size,
                    onSelect = { onSizeChange(size) },
                ) {
                    Text(
                        text = "Aa",
                        style = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp * size.scale),
                        fontWeight = FontWeight.Medium,
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            // Line spacing
            Text(
                text = "Line spacing",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            for (spacing in LineSpacing.entries) {
                RadioOptionRow(
                    label = spacing.label,
                    selected = currentSpacing == spacing,
                    onSelect = { onSpacingChange(spacing) },
                )
            }
        }
    }
}

@Composable
private fun ReadingThemeRow(
    label: String,
    selected: Boolean,
    onSelect: () -> Unit,
    previewColor: androidx.compose.ui.graphics.Color,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            color = previewColor,
            shape = RoundedCornerShape(4.dp),
            modifier = Modifier.size(32.dp),
        ) {
            androidx.compose.foundation.layout.Box(Modifier.fillMaxSize())
        }
        Spacer(Modifier.width(12.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
        )
        if (selected) {
            Icon(
                painter = painterResource(R.drawable.lucide_ic_check),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp),
            )
        }
    }
}

@Composable
private fun FontRow(
    label: String,
    selected: Boolean,
    onSelect: () -> Unit,
    fontFamily: FontFamily,
) {
    val typeface = when (fontFamily) {
        FontFamily.SERIF -> androidx.compose.ui.text.font.FontFamily.Serif
        FontFamily.MONOSPACE -> androidx.compose.ui.text.font.FontFamily.Monospace
        FontFamily.SANS_SERIF -> androidx.compose.ui.text.font.FontFamily.Default
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "Aa",
            style = MaterialTheme.typography.bodyLarge.copy(
                fontFamily = typeface,
                fontSize = 18.sp,
            ),
            fontWeight = FontWeight.Medium,
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
        )
        if (selected) {
            Icon(
                painter = painterResource(R.drawable.lucide_ic_check),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp),
            )
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
            .clickable(onClick = onSelect)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            RadioButton(selected = selected, onClick = onSelect)
            Spacer(Modifier.width(8.dp))
            Text(label, style = MaterialTheme.typography.bodyLarge)
        }
        trailing?.invoke()
    }
}

private fun shareArticle(context: Context, article: ArticleContent) {
    val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(android.content.Intent.EXTRA_SUBJECT, article.title)
        putExtra(android.content.Intent.EXTRA_TEXT, GrimoireArticleRepository.toPlainText(article))
    }
    context.startActivity(android.content.Intent.createChooser(intent, "Share article via"))
}

private fun speakArticle(
    context: Context,
    tts: TextToSpeech,
    article: ArticleContent,
) {
    val text = GrimoireArticleRepository.toPlainText(article)
    tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "grimoire_utterance")
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

// Extension properties for UI display labels
private val ReadingTheme.label: String
    get() = when (this) {
        ReadingTheme.DEFAULT -> "Default"
        ReadingTheme.SEPIA -> "Sepia"
        ReadingTheme.AMBER -> "Amber"
    }

private val FontFamily.label: String
    get() = when (this) {
        FontFamily.SANS_SERIF -> "Sans-serif"
        FontFamily.SERIF -> "Serif"
        FontFamily.MONOSPACE -> "Monospace"
    }
