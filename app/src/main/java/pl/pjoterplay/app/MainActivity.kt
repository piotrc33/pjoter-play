package pl.pjoterplay.app

import android.Manifest
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import pl.pjoterplay.app.catalog.CatalogApp
import pl.pjoterplay.app.catalog.MANAGER_ID
import pl.pjoterplay.app.ui.AppsScreen
import pl.pjoterplay.app.ui.PjoterPlayTheme
import pl.pjoterplay.app.updates.UpdateCheckWorker

class MainActivity : ComponentActivity() {
    private val apps: AppsViewModel by viewModels()

    private val askNotifications = registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PjoterPlayTheme {
                val state by apps.state.collectAsStateWithLifecycle()
                AppsScreen(state, onRefresh = apps::refresh, onInstall = apps::install, onOpen = ::open)
            }
        }
        // Only the release build checks daily: with the debug one beside it, every update would be announced twice.
        if (packageName == MANAGER_ID) UpdateCheckWorker.schedule(this)
        // Debug: `am start -n pl.pjoterplay.app.debug/pl.pjoterplay.app.MainActivity --ez check_updates true`.
        val debuggable = applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
        if (debuggable && intent.getBooleanExtra("check_updates", false)) UpdateCheckWorker.runOnce(this)
        if (savedInstanceState == null) askForNotificationsOnce()
    }

    // Once: after a "no" the system's dialog would come back on every start (and after two it shows no more anyway).
    private fun askForNotificationsOnce() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val prefs = getSharedPreferences("main", Context.MODE_PRIVATE)
        if (prefs.getBoolean("askedNotifications", false)) return
        prefs.edit { putBoolean("askedNotifications", true) }
        askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    private fun open(app: CatalogApp) {
        packageManager.getLaunchIntentForPackage(app.id)?.let(::startActivity)
    }

    override fun onResume() {
        super.onResume()
        apps.onResume()
        // The list shows the updates now; the next check announces only new ones.
        NotificationManagerCompat.from(this).cancel(UpdateCheckWorker.NOTIFICATION_ID)
    }
}
