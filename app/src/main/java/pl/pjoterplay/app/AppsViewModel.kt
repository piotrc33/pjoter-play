package pl.pjoterplay.app

import android.app.Application
import android.os.Build
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

data class AppItem(val app: CatalogApp, val status: AppStatus)

enum class LoadError { Network, ManagerTooOld }

data class AppsState(
    /** Null until the first successful download. */
    val items: List<AppItem>? = null,
    val loading: Boolean = false,
    val error: LoadError? = null,
)

class AppsViewModel(application: Application) : AndroidViewModel(application) {
    private val _state = MutableStateFlow(AppsState())
    val state: StateFlow<AppsState> = _state

    private var catalog: Catalog? = null

    init {
        refresh()
    }

    fun refresh() {
        if (_state.value.loading) return
        _state.update { it.copy(loading = true) }
        viewModelScope.launch {
            val error = try {
                catalog = fetchCatalog()
                null
            } catch (e: UnsupportedCatalogException) {
                Log.w(TAG, "Catalog too new", e)
                LoadError.ManagerTooOld
            } catch (e: Exception) {
                Log.w(TAG, "Catalog download failed", e)
                LoadError.Network
            }
            _state.update { it.copy(items = items() ?: it.items, loading = false, error = error) }
        }
    }

    /** Without the network: on return to the app, after the user installed or removed something. */
    fun refreshInstalled() {
        items()?.let { items -> _state.update { it.copy(items = items) } }
    }

    private fun items(): List<AppItem>? {
        val packages = getApplication<Application>().packageManager
        return catalog?.apps?.filter { it.id != MANAGER_ID }?.map { app ->
            AppItem(app, statusOf(app, packages.installedVersion(app.id), Build.VERSION.SDK_INT))
        }
    }

    private companion object {
        const val TAG = "AppsViewModel"
    }
}
