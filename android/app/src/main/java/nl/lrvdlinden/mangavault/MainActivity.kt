package nl.lrvdlinden.mangavault

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.SystemBarStyle
import androidx.compose.runtime.CompositionLocalProvider
import androidx.lifecycle.lifecycleScope
import nl.lrvdlinden.mangavault.app.AppState
import nl.lrvdlinden.mangavault.app.LocalApp
import nl.lrvdlinden.mangavault.core.Base44
import nl.lrvdlinden.mangavault.ui.main.RootScreen
import nl.lrvdlinden.mangavault.ui.theme.MangaVaultTheme

class MainActivity : ComponentActivity() {
    private lateinit var app: AppState

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(0xFF121212.toInt()),
            navigationBarStyle = SystemBarStyle.dark(0xFF121212.toInt()),
        )
        Base44.init(applicationContext)
        app = AppState(lifecycleScope)
        app.bootstrap()
        // Only on a fresh launch: after a configuration change the same intent would replay an old auth token.
        if (savedInstanceState == null) intent?.data?.let { app.handle(it) }
        setContent {
            MangaVaultTheme {
                CompositionLocalProvider(LocalApp provides app) { RootScreen() }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.data?.let { app.handle(it) }
    }
}
