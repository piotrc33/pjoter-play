package pl.pjoterplay.app.install

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import android.util.Log
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

sealed interface InstallResult {
    val appId: String

    data class Installed(override val appId: String) : InstallResult

    /** The user said no in the system's dialog. */
    data class Cancelled(override val appId: String) : InstallResult

    data class Failed(override val appId: String, val status: Int, val message: String?) : InstallResult
}

/** Results from [InstallReceiver] to the view model, which lives as long as the activity's task. */
object InstallResults {
    // Replay 0, a small buffer: tryEmit from the receiver must not drop a result while the collector is busy.
    internal val flow = MutableSharedFlow<InstallResult>(extraBufferCapacity = 8)
    val results: SharedFlow<InstallResult> = flow
}

class InstallReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val appId = intent.getStringExtra(EXTRA_APP_ID) ?: return
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        val result = when (status) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                // The system's "install this app?" dialog (or, the first time, the "unknown apps" permission). The
                // user has just tapped the button in this app, which is in the foreground, so it may start it.
                confirmation(intent)?.let { context.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                return
            }
            PackageInstaller.STATUS_SUCCESS -> InstallResult.Installed(appId)
            PackageInstaller.STATUS_FAILURE_ABORTED -> InstallResult.Cancelled(appId)
            else -> InstallResult.Failed(appId, status, intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE))
        }
        Log.i(TAG, "$appId: $result")
        InstallResults.flow.tryEmit(result)
    }

    private fun confirmation(intent: Intent): Intent? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(Intent.EXTRA_INTENT)
        }

    companion object {
        const val EXTRA_APP_ID = "pl.pjoterplay.app.APP_ID"
        private const val TAG = "InstallReceiver"
    }
}
