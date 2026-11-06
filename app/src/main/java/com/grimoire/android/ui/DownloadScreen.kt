package com.grimoire.android.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.grimoire.android.R
import com.grimoire.android.data.GrimoireDatabase

/**
 * First-launch flow: download the grimoire database once from GitHub releases,
 * verify it against the release's SHA-256 manifest, keep it offline forever.
 */
@Composable
fun DownloadScreen(viewModel: AppViewModel) {
    val downloadState by viewModel.downloadState.collectAsState()
    val isFailed = downloadState is GrimoireDatabase.DlState.Failed

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            painter = painterResource(R.drawable.lucide_ic_scroll_text),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = "Grimoire",
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = "Search the Destiny lore archive, offline.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(28.dp))

        ElevatedCard(
            modifier = Modifier.widthIn(max = 520.dp),
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                val dl = downloadState
                when (dl) {
                    GrimoireDatabase.DlState.Connecting -> DownloadStatus(
                        title = "Connecting to GitHub…",
                        progress = null,
                    )
                    is GrimoireDatabase.DlState.Downloading -> {
                        val total = dl.totalBytes.coerceAtLeast(1L)
                        DownloadStatus(
                            title = "Downloading the archive…",
                            progress = (dl.downloadedBytes.toFloat() / total.toFloat()),
                            detail = "${formatMb(dl.downloadedBytes)} of ${formatMb(dl.totalBytes)} MB · " +
                                "only download, ever",
                        )
                    }
                    GrimoireDatabase.DlState.VerifyingCompressed -> DownloadStatus(
                        title = "Verifying checksum…",
                        progress = null,
                        detail = "Confirming the .gz matches the release manifest (SHA-256)",
                    )
                    GrimoireDatabase.DlState.Decompressing -> DownloadStatus(
                        title = "Unpacking the archive…",
                        progress = null,
                        detail = "Decompressing the SQLite database",
                    )
                    GrimoireDatabase.DlState.VerifyingDecompressed -> DownloadStatus(
                        title = "Verifying checksum…",
                        progress = null,
                        detail = "Confirming the unpacked database matches the manifest",
                    )
                    GrimoireDatabase.DlState.Ready -> DownloadStatus(
                        title = "Ready.",
                        progress = 1f,
                    )
                    is GrimoireDatabase.DlState.Failed -> {
                        Icon(
                            painter = painterResource(R.drawable.lucide_ic_circle_alert),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            text = "Download failed",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.error,
                        )
                        Text(
                            text = dl.message,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                    }
                }

                Spacer(Modifier.height(20.dp))

                when {
                    isFailed -> Button(onClick = { viewModel.startDownload() }) {
                        Icon(painter = painterResource(R.drawable.lucide_ic_refresh_cw), contentDescription = null)
                        Spacer(Modifier.padding(4.dp))
                        Text("Retry download")
                    }
                    downloadState == GrimoireDatabase.DlState.Connecting ||
                        downloadState is GrimoireDatabase.DlState.Downloading ||
                        downloadState == GrimoireDatabase.DlState.Decompressing ||
                        downloadState == GrimoireDatabase.DlState.VerifyingCompressed ||
                        downloadState == GrimoireDatabase.DlState.VerifyingDecompressed -> {
                        // In progress — nothing to tap
                    }
                    else -> Button(onClick = { viewModel.startDownload() }) {
                        Icon(painter = painterResource(R.drawable.lucide_ic_download), contentDescription = null)
                        Spacer(Modifier.padding(4.dp))
                        Text("Download now (~56 MB)")
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        Text(
            text = "The archive is downloaded once from the publisher's GitHub release and kept offline on your device. " +
                "No account, no tracking. Checksums are verified against the release's SHA256SUMS manifest before use.",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 520.dp),
        )
    }
}

@Composable
private fun DownloadStatus(title: String, progress: Float?, detail: String? = null) {
    val p = progress
    if (p == null) {
        CircularProgressIndicator()
    } else if (p < 1f) {
        LinearProgressIndicator(progress = { p })
    } else {
        Icon(
            painter = painterResource(R.drawable.lucide_ic_check),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
        )
    }
    Spacer(Modifier.height(12.dp))
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurface,
    )
    if (detail != null) {
        Text(
            text = detail,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

private fun formatMb(bytes: Long): String {
    val mb = bytes.toDouble() / (1024.0 * 1024.0)
    return "%.1f".format(mb)
}
