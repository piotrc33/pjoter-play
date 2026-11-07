package pl.pjoterplay.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
                AppsScreen(state, onRefresh = apps::refresh)
            }
        }
    }

    // The user may have installed or removed an app while away (also through the system installer's screen).
    override fun onResume() {
        super.onResume()
        apps.refreshInstalled()
    }
}
