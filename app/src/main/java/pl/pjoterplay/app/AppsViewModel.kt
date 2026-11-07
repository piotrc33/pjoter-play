package pl.pjoterplay.app

import android.app.Application
import android.content.pm.PackageInstaller
import android.os.Build
import android.os.SystemClock
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import pl.pjoterplay.app.catalog.Catalog
import pl.pjoterplay.app.catalog.CatalogApp
import pl.pjoterplay.app.catalog.MANAGER_ID
import pl.pjoterplay.app.catalog.UnsupportedCatalogException
import pl.pjoterplay.app.catalog.fetchCatalog
import pl.pjoterplay.app.install.CorruptDownloadException
import pl.pjoterplay.app.install.InstallResult
import pl.pjoterplay.app.install.InstallResults
import pl.pjoterplay.app.install.download
import pl.pjoterplay.app.install.install
import java.io.IOException

/** What the manager is doing with one app; none when idle. */
sealed interface Operation {
    /** 0..1. */
    data class Downloading(val progress: Float) : Operation

    /** With the system installer, which may be showing its dialog. */
    data object Installing : Operation

    data class Failed(val reason: FailureReason) : Operation
}

enum class FailureReason { Network, Corrupt, SignatureConflict, Storage, Incompatible, Other }

data class AppItem(val app: CatalogApp, val status: AppStatus, val operation: Operation? = null)

enum class LoadError { Network, ManagerTooOld }

data class AppsState(
    /** Null until the first successful download. */
    val items: List<AppItem>? = null,
    /** This manager's own update, while one is available or being installed. */
    val managerUpdate: AppItem? = null,
    val loading: Boolean = false,
    val error: LoadError? = null,
)

class AppsViewModel(application: Application) : AndroidViewModel(application) {
    private val _state = MutableStateFlow(AppsState())
    val state: StateFlow<AppsState> = _state

    private var catalog: Catalog? = null
    /** SystemClock.elapsedRealtime() of the last successful download; 0 = never. */
    private var fetchedAt = 0L
    private val operations = mutableMapOf<String, Operation>()

    init {
        refresh()
        viewModelScope.launch { InstallResults.results.collect(::onInstallResult) }
    }

    fun refresh() {
        if (_state.value.loading) return
        _state.update { it.copy(loading = true) }
        viewModelScope.launch {
            val error = try {
                catalog = fetchCatalog()
                fetchedAt = SystemClock.elapsedRealtime()
                null
            } catch (e: UnsupportedCatalogException) {
                Log.w(TAG, "Catalog too new", e)
                LoadError.ManagerTooOld
            } catch (e: Exception) {
                Log.w(TAG, "Catalog download failed", e)
                LoadError.Network
            }
            _state.update {
                it.copy(items = items() ?: it.items, managerUpdate = managerUpdate(), loading = false, error = error)
            }
        }
    }

    /**
     * On return to the app: the user may have installed or removed something meanwhile, and the process may have been
     * alive for hours, with a catalog from before the latest release (the user once saw no manager update that way).
     */
    fun onResume() {
        publish()
        if (isStale(fetchedAt, SystemClock.elapsedRealtime())) refresh()
    }

    fun install(app: CatalogApp) {
        val release = app.release ?: return
        if (operations[app.id].let { it is Operation.Downloading || it is Operation.Installing }) return
        val context = getApplication<Application>()
        set(app.id, Operation.Downloading(0f))
        viewModelScope.launch {
            try {
                val apk = download(context, app.id, release) { set(app.id, Operation.Downloading(it)) }
                set(app.id, Operation.Installing)
                install(context, app.id, apk)
            } catch (e: CorruptDownloadException) {
                Log.w(TAG, "${app.id}: corrupt download", e)
                set(app.id, Operation.Failed(FailureReason.Corrupt))
            } catch (e: IOException) {
                Log.w(TAG, "${app.id}: download failed", e)
                set(app.id, Operation.Failed(FailureReason.Network))
            } catch (e: Exception) {
                Log.w(TAG, "${app.id}: install failed", e)
                set(app.id, Operation.Failed(FailureReason.Other))
            }
        }
    }

    private fun onInstallResult(result: InstallResult) {
        when (result) {
            is InstallResult.Installed, is InstallResult.Cancelled -> set(result.appId, null)
            is InstallResult.Failed -> set(result.appId, Operation.Failed(reasonOf(result.status)))
        }
    }

    private fun set(appId: String, operation: Operation?) {
        if (operation == null) operations.remove(appId) else operations[appId] = operation
        publish()
    }

    private fun publish() {
        items()?.let { items -> _state.update { it.copy(items = items, managerUpdate = managerUpdate()) } }
    }

    private fun items(): List<AppItem>? {
        val packages = getApplication<Application>().packageManager
        return catalog?.apps?.filter { it.id != MANAGER_ID }?.map { app ->
            AppItem(app, statusOf(app, packages.installedVersion(app.id), Build.VERSION.SDK_INT), operations[app.id])
        }
    }

    private fun managerUpdate(): AppItem? {
        val context = getApplication<Application>()
        // Only the release build: the debug one (".debug") would "update" to the release, another app beside it.
        if (context.packageName != MANAGER_ID) return null
        val manager = catalog?.apps?.find { it.id == MANAGER_ID } ?: return null
        val item = AppItem(
            manager,
            statusOf(manager, context.packageManager.installedVersion(MANAGER_ID), Build.VERSION.SDK_INT),
            operations[MANAGER_ID],
        )
        return item.takeIf { it.status is AppStatus.UpdateAvailable || it.operation != null }
    }

    private companion object {
        const val TAG = "AppsViewModel"
    }
}

// GitHub Pages' CDN keeps apps.json for 10 minutes anyway (max-age=600), so more often would rarely find anything new.
private const val CATALOG_MAX_AGE_MS = 5 * 60_000L

fun isStale(fetchedAt: Long, now: Long): Boolean = fetchedAt != 0L && now - fetchedAt > CATALOG_MAX_AGE_MS

fun reasonOf(installerStatus: Int): FailureReason = when (installerStatus) {
    // Signed with another key (e.g. a build from elsewhere): only uninstalling, which deletes its data, helps.
    PackageInstaller.STATUS_FAILURE_CONFLICT -> FailureReason.SignatureConflict
    PackageInstaller.STATUS_FAILURE_STORAGE -> FailureReason.Storage
    PackageInstaller.STATUS_FAILURE_INCOMPATIBLE -> FailureReason.Incompatible
    else -> FailureReason.Other
}
