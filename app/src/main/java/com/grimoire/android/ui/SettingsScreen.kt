package com.grimoire.android.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.grimoire.android.R
import com.grimoire.android.data.FontFamily
import com.grimoire.android.data.GrimoireDatabase
import com.grimoire.android.data.LineSpacing
import com.grimoire.android.data.ReadingSize
import com.grimoire.android.data.ReadingTheme
import com.grimoire.android.data.ThemeMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: AppViewModel,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val settings by viewModel.settings.collectAsState()
    val bookmarks by viewModel.bookmarks.collectAsState()
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showLicensesDialog by remember { mutableStateOf(false) }

    val archiveSize = remember { formatSize(GrimoireDatabase.dbFile(context).length()) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            painter = painterResource(R.drawable.lucide_ic_arrow_left),
                            contentDescription = "Back",
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(),
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 24.dp),
        ) {
            // ---------- Appearance ----------
            item { SectionHeader("Appearance", R.drawable.lucide_ic_palette) }
            item {
                ThemeRadioRow(
                    label = "System default",
                    icon = R.drawable.lucide_ic_sun_moon,
                    selected = settings.themeMode == ThemeMode.SYSTEM,
                    onSelect = { viewModel.onThemeModeChange(ThemeMode.SYSTEM) },
                )
            }
            item {
                ThemeRadioRow(
                    label = "Light",
                    icon = R.drawable.lucide_ic_sun,
                    selected = settings.themeMode == ThemeMode.LIGHT,
                    onSelect = { viewModel.onThemeModeChange(ThemeMode.LIGHT) },
                )
            }
            item {
                ThemeRadioRow(
                    label = "Dark",
                    icon = R.drawable.lucide_ic_moon,
                    selected = settings.themeMode == ThemeMode.DARK,
                    onSelect = { viewModel.onThemeModeChange(ThemeMode.DARK) },
                )
            }
            item {
                SwitchRow(
                    icon = R.drawable.lucide_ic_palette,
                    title = "Dynamic color",
                    subtitle = "Match the app to your wallpaper (Material You, Android 12+)",
                    checked = settings.dynamicColor,
                    onCheckedChange = { viewModel.onDynamicColorChange(it) },
                )
            }

            // ---------- Reading ----------
            item { SectionHeader("Reading", R.drawable.lucide_ic_type) }
            item {
                ReadingThemeRow(
                    label = "Default",
                    icon = R.drawable.lucide_ic_file_text,
                    selected = settings.readingTheme == ReadingTheme.DEFAULT,
                    onSelect = { viewModel.onReadingThemeChange(ReadingTheme.DEFAULT) },
                )
            }
            item {
                ReadingThemeRow(
                    label = "Sepia",
                    icon = R.drawable.lucide_ic_file_text,
                    selected = settings.readingTheme == ReadingTheme.SEPIA,
                    onSelect = { viewModel.onReadingThemeChange(ReadingTheme.SEPIA) },
                    previewColor = androidx.compose.ui.graphics.Color(0xFFF5F0E6),
                )
            }
            item {
                ReadingThemeRow(
                    label = "Amber",
                    icon = R.drawable.lucide_ic_file_text,
                    selected = settings.readingTheme == ReadingTheme.AMBER,
                    onSelect = { viewModel.onReadingThemeChange(ReadingTheme.AMBER) },
                    previewColor = androidx.compose.ui.graphics.Color(0xFF1A140A),
                )
            }
            item {
                FontFamilyRow(
                    label = "Sans-serif",
                    selected = settings.fontFamily == FontFamily.SANS_SERIF,
                    onSelect = { viewModel.onFontFamilyChange(FontFamily.SANS_SERIF) },
                )
            }
            item {
                FontFamilyRow(
                    label = "Serif",
                    selected = settings.fontFamily == FontFamily.SERIF,
                    onSelect = { viewModel.onFontFamilyChange(FontFamily.SERIF) },
                    fontStyle = androidx.compose.ui.text.font.FontStyle.Normal,
                )
            }
            item {
                FontFamilyRow(
                    label = "Monospace",
                    selected = settings.fontFamily == FontFamily.MONOSPACE,
                    onSelect = { viewModel.onFontFamilyChange(FontFamily.MONOSPACE) },
                )
            }
            items(ReadingSize.entries.toList()) { size ->
                RadioRow(
                    title = size.label,
                    selected = settings.readingSize == size,
                    onSelect = { viewModel.onReadingSizeChange(size) },
                    trailing = {
                        Text(
                            text = "Aa",
                            style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp * size.scale),
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                )
            }
            item {
                Text(
                    text = "Line spacing",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 72.dp, top = 4.dp),
                )
            }
            items(LineSpacing.entries.toList()) { spacing ->
                RadioRow(
                    title = spacing.label,
                    selected = settings.lineSpacing == spacing,
                    onSelect = { viewModel.onLineSpacingChange(spacing) },
                )
            }

            // ---------- Bookmarks ----------
            item { SectionHeader("Bookmarks", R.drawable.lucide_ic_bookmark) }
            item {
                SettingRow(
                    icon = R.drawable.lucide_ic_bookmark,
                    title = "Your bookmarks",
                    subtitle = "${bookmarks.size} saved article${if (bookmarks.size != 1) "s" else ""}",
                    onClick = null,
                    titleColor = MaterialTheme.colorScheme.onSurface,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                ) {
                    Text(
                        text = if (bookmarks.isEmpty()) "None yet" else "Tap to view",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // ---------- Data ----------
            item { SectionHeader("Archive", R.drawable.lucide_ic_database) }
            item {
                SettingRow(
                    icon = R.drawable.lucide_ic_file_text,
                    title = "Archive size",
                    subtitle = "One-time offline download, kept on this device",
                    onClick = null,
                    titleColor = MaterialTheme.colorScheme.onSurface,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                ) {
                    Text(
                        text = archiveSize,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            item {
                SettingRow(
                    icon = R.drawable.lucide_ic_refresh_cw,
                    title = "Download latest archive",
                    subtitle = "Fetches the newest database from the publisher's release",
                    onClick = {
                        viewModel.deleteDatabaseAndRedownload()
                        onBack()
                    },
                    titleColor = MaterialTheme.colorScheme.onSurface,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.lucide_ic_chevron_right),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            item {
                SettingRow(
                    icon = R.drawable.lucide_ic_trash_2,
                    title = "Delete offline copy",
                    subtitle = "Removes the archive; you can re-download it anytime",
                    titleColor = MaterialTheme.colorScheme.error,
                    tint = MaterialTheme.colorScheme.error,
                    onClick = { showDeleteDialog = true },
                ) {
                    Icon(
                        painter = painterResource(R.drawable.lucide_ic_chevron_right),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
                    )
                }
            }

            // ---------- About ----------
            item { SectionHeader("About", R.drawable.lucide_ic_info) }
            item {
                SettingRow(
                    icon = R.drawable.lucide_ic_info,
                    title = "Version",
                    subtitle = null,
                    onClick = null,
                    titleColor = MaterialTheme.colorScheme.onSurface,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                ) {
                    Text(
                        text = "0.2.0 (3)",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            item {
                SettingRow(
                    icon = R.drawable.lucide_ic_shield_check,
                    title = "Open-source licenses",
                    subtitle = "SQLite, Compose, Lucide and more",
                    onClick = { showLicensesDialog = true },
                    titleColor = MaterialTheme.colorScheme.onSurface,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.lucide_ic_chevron_right),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            item {
                HorizontalDivider(modifier = Modifier.padding(top = 8.dp))
                Text(
                    text = "Destiny is a trademark of Bungie Inc. Fan-built project; not affiliated " +
                        "with or endorsed by Bungie. Lore content compiled from The Ishtar Collective, " +
                        "Destinypedia and reviewed captions by sagaciouszu/destiny-agent-grimoire.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                )
            }
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            icon = { Icon(painterResource(R.drawable.lucide_ic_trash_2), contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Delete offline copy?") },
            text = { Text("This removes the downloaded archive (about 215 MB) from this device. You can download it again from Settings → Archive.") },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteDialog = false
                    viewModel.deleteDatabaseAndRedownload()
                    onBack()
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("Cancel") }
            },
        )
    }

    if (showLicensesDialog) {
        AlertDialog(
            onDismissRequest = { showLicensesDialog = false },
            icon = { Icon(painterResource(R.drawable.lucide_ic_shield_check), contentDescription = null) },
            title = { Text("Open-source licenses") },
            text = {
                Text(
                    "• SQLite 3.54.1 — public domain\n" +
                        "• SQLite FTS5 — public domain\n" +
                        "• AndroidX / Jetpack Compose / Material 3 — Apache License 2.0\n" +
                        "• Lucide icons — ISC\n" +
                        "• composablehorizons/lucide-icons-android — MIT\n" +
                        "• Kotlin — Apache License 2.0",
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            confirmButton = { TextButton(onClick = { showLicensesDialog = false }) { Text("Close") } },
        )
    }
}

@Composable
private fun SectionHeader(title: String, icon: Int) {
    Row(
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = title.uppercase(),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
private fun ThemeRadioRow(label: String, icon: Int, selected: Boolean, onSelect: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect)
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(24.dp),
        )
        Spacer(Modifier.width(16.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        RadioButton(selected = selected, onClick = onSelect)
    }
}

@Composable
private fun ReadingThemeRow(
    label: String,
    icon: Int,
    selected: Boolean,
    onSelect: () -> Unit,
    previewColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.surface,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect)
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            color = previewColor,
            shape = RoundedCornerShape(4.dp),
            modifier = Modifier.size(24.dp),
        ) {
            androidx.compose.foundation.layout.Box(Modifier.fillMaxSize())
        }
        Spacer(Modifier.width(16.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        RadioButton(selected = selected, onClick = onSelect)
    }
}

@Composable
private fun FontFamilyRow(
    label: String,
    selected: Boolean,
    onSelect: () -> Unit,
    fontStyle: androidx.compose.ui.text.font.FontStyle = androidx.compose.ui.text.font.FontStyle.Normal,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect)
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "Aa",
            style = MaterialTheme.typography.bodyLarge.copy(fontStyle = fontStyle),
            fontWeight = FontWeight.Medium,
        )
        Spacer(Modifier.width(16.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        RadioButton(selected = selected, onClick = onSelect)
    }
}

@Composable
private fun RadioRow(
    title: String,
    selected: Boolean,
    onSelect: () -> Unit,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect)
            .padding(start = 72.dp, end = 20.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        trailing?.invoke()
        Spacer(Modifier.width(4.dp))
        RadioButton(selected = selected, onClick = onSelect)
    }
}

@Composable
private fun SwitchRow(
    icon: Int,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(24.dp),
        )
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun SettingRow(
    icon: Int,
    title: String,
    subtitle: String?,
    onClick: (() -> Unit)?,
    titleColor: androidx.compose.ui.graphics.Color,
    tint: androidx.compose.ui.graphics.Color,
    trailing: @Composable () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(24.dp),
        )
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = titleColor)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        trailing()
    }
}

private fun formatSize(bytes: Long): String {
    val mb = bytes.toDouble() / (1024.0 * 1024.0)
    return if (mb >= 1) "%.0f MB".format(mb) else "%.0f KB".format(bytes.toDouble() / 1024.0)
}
