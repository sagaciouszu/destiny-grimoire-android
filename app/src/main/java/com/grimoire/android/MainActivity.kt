package com.grimoire.android

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.grimoire.android.data.ThemeMode
import com.grimoire.android.ui.AppViewModel
import com.grimoire.android.ui.ArticleScreen
import com.grimoire.android.ui.DatabaseState
import com.grimoire.android.ui.DownloadScreen
import com.grimoire.android.ui.SearchScreen
import com.grimoire.android.ui.SettingsScreen
import com.grimoire.android.ui.theme.GrimoireTheme

class MainActivity : ComponentActivity() {

    private val viewModel: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val settings by viewModel.settings.collectAsStateWithLifecycle()
            val darkTheme = when (settings.themeMode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            GrimoireTheme(darkTheme = darkTheme, dynamicColor = settings.dynamicColor) {
                GrimoireApp(viewModel)
            }
        }
    }
}

@Composable
private fun GrimoireApp(viewModel: AppViewModel) {
    val dbState by viewModel.dbState.collectAsStateWithLifecycle()
    when (dbState) {
        DatabaseState.CHECKING -> CheckingSplash()
        DatabaseState.READY -> {
            val navController = rememberNavController()
            NavHost(navController = navController, startDestination = "home") {
                composable("home") {
                    SearchScreen(
                        viewModel = viewModel,
                        onOpenArticle = { source, slug ->
                            navController.navigate("article/${Uri.encode(source)}/${Uri.encode(slug)}")
                        },
                        onOpenSettings = { navController.navigate("settings") },
                    )
                }
                composable("article/{source}/{sourceSlug}") { entry ->
                    val source = Uri.decode(entry.arguments?.getString("source") ?: "")
                    val slug = Uri.decode(entry.arguments?.getString("sourceSlug") ?: "")
                    ArticleScreen(
                        source = source,
                        sourceSlug = slug,
                        viewModel = viewModel,
                        onBack = { navController.popBackStack() },
                    )
                }
                composable("settings") {
                    SettingsScreen(
                        viewModel = viewModel,
                        onBack = { navController.popBackStack() },
                    )
                }
            }
        }
        DatabaseState.NEEDS_DOWNLOAD,
        DatabaseState.DOWNLOADING,
        DatabaseState.FAILED -> DownloadScreen(viewModel)
    }
}

@Composable
private fun CheckingSplash() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
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
