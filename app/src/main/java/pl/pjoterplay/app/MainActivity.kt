package pl.pjoterplay.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import pl.pjoterplay.app.catalog.CatalogApp
import pl.pjoterplay.app.ui.AppsScreen
import pl.pjoterplay.app.ui.PjoterPlayTheme

class MainActivity : ComponentActivity() {
    private val apps: AppsViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PjoterPlayTheme {
                val state by apps.state.collectAsStateWithLifecycle()
                AppsScreen(state, onRefresh = apps::refresh, onInstall = apps::install, onOpen = ::open)
            }
        }
    }

    private fun open(app: CatalogApp) {
        packageManager.getLaunchIntentForPackage(app.id)?.let(::startActivity)
    }

    override fun onResume() {
        super.onResume()
        apps.onResume()
    }
}
