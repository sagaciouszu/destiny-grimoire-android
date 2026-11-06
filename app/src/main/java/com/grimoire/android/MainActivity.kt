package com.grimoire.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.grimoire.android.ui.AppViewModel
import com.grimoire.android.ui.DatabaseState
import com.grimoire.android.ui.DownloadScreen
import com.grimoire.android.ui.SearchScreen
import com.grimoire.android.ui.theme.GrimoireTheme

class MainActivity : ComponentActivity() {

    private val viewModel: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            GrimoireTheme {
                val dbState by viewModel.dbState.collectAsStateWithLifecycle()
                when (dbState) {
                    DatabaseState.CHECKING -> CheckingSplash()
                    DatabaseState.NEEDS_DOWNLOAD,
                    DatabaseState.DOWNLOADING,
                    DatabaseState.FAILED -> DownloadScreen(viewModel)
                    DatabaseState.READY -> SearchScreen(viewModel)
                }
            }
        }
    }
}

@Composable
private fun CheckingSplash() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(12.dp))
            Text(
                text = "Opening the grimoire…",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
