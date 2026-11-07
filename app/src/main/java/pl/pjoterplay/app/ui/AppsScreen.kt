package pl.pjoterplay.app.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import pl.pjoterplay.app.AppAction
import pl.pjoterplay.app.AppItem
import pl.pjoterplay.app.AppStatus
import pl.pjoterplay.app.AppsState
import pl.pjoterplay.app.FailureReason
import pl.pjoterplay.app.LoadError
import pl.pjoterplay.app.Operation
import pl.pjoterplay.app.actionOf
import pl.pjoterplay.app.androidVersionName
import pl.pjoterplay.app.catalog.CatalogApp
import pl.pjoterplay.app.catalog.iconUrl
import pl.pjoterplay.app.catalog.loadIcon

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppsScreen(
    state: AppsState,
    onRefresh: () -> Unit,
    onInstall: (CatalogApp) -> Unit,
    onOpen: (CatalogApp) -> Unit,
) {
    Scaffold(topBar = { TopAppBar(title = { Text("Pjoter Play") }) }) { padding ->
        val items = state.items
        when {
            items != null -> PullToRefreshBox(
                isRefreshing = state.loading,
                onRefresh = onRefresh,
                modifier = Modifier.padding(padding).fillMaxSize(),
            ) {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    // Refreshing keeps the old list: only say the new one couldn't be fetched.
                    state.error?.let { error -> item { Text(errorText(error), color = MaterialTheme.colorScheme.error) } }
                    items(items, key = { it.app.id }) { AppCard(it, onInstall, onOpen) }
                }
            }
            state.loading -> Box(Modifier.padding(padding).fillMaxSize(), Alignment.Center) {
                CircularProgressIndicator()
            }
            else -> ErrorMessage(state.error ?: LoadError.Network, onRefresh, Modifier.padding(padding))
        }
    }
}

private fun errorText(error: LoadError) = when (error) {
    LoadError.Network -> "Nie udało się pobrać listy aplikacji. Sprawdź połączenie z internetem."
    LoadError.ManagerTooOld -> "Ta wersja Pjoter Play jest za stara na nową listę aplikacji. Pobierz najnowszą ze strony."
}

@Composable
private fun ErrorMessage(error: LoadError, onRetry: () -> Unit, modifier: Modifier) {
    Column(
        modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(errorText(error), textAlign = TextAlign.Center)
        Spacer(Modifier.height(16.dp))
        Button(onClick = onRetry) { Text("Spróbuj ponownie") }
    }
}

@Composable
private fun AppCard(item: AppItem, onInstall: (CatalogApp) -> Unit, onOpen: (CatalogApp) -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            AppIcon(iconUrl(item.app))
            Column {
                Text(item.app.name, style = MaterialTheme.typography.titleMedium)
                Text(item.app.summary, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(4.dp))
                Text(
                    item.app.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    statusText(item.status),
                    style = MaterialTheme.typography.labelLarge,
                    color = if (item.status is AppStatus.UpdateAvailable) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
                OperationRow(item, onInstall, onOpen)
            }
        }
    }
}

private fun statusText(status: AppStatus) = when (status) {
    AppStatus.NotReleased -> "W przygotowaniu"
    is AppStatus.Unsupported -> "Wymaga Androida ${androidVersionName(status.minSdk)} lub nowszego"
    is AppStatus.NotInstalled -> "Nie zainstalowano · wersja ${status.release.versionName}"
    is AppStatus.UpToDate -> "Zainstalowano · wersja ${status.installed.name}"
    is AppStatus.UpdateAvailable ->
        "Dostępna aktualizacja: ${status.installed.name} → ${status.release.versionName}"
}

// The launcher's rounded square (the icons are full-bleed, CLAUDE.md, Layout).
private val iconShape = RoundedCornerShape(percent = 22)

@Composable
private fun AppIcon(url: String) {
    val icon by produceState<ImageBitmap?>(null, url) { value = loadIcon(url) }
    val modifier = Modifier.size(56.dp).clip(iconShape)
    icon?.let { Image(it, contentDescription = null, modifier = modifier) }
        ?: Box(modifier.background(MaterialTheme.colorScheme.surfaceVariant))
}

@Composable
private fun OperationRow(item: AppItem, onInstall: (CatalogApp) -> Unit, onOpen: (CatalogApp) -> Unit) {
    when (val operation = item.operation) {
        is Operation.Downloading -> Progress("Pobieranie… ${(operation.progress * 100).toInt()}%") {
            LinearProgressIndicator(progress = { operation.progress }, modifier = Modifier.fillMaxWidth())
        }
        Operation.Installing -> Progress("Instalowanie…") {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
        is Operation.Failed, null -> {
            if (operation is Operation.Failed) {
                Spacer(Modifier.height(8.dp))
                Text(
                    failureText(operation.reason),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            val action = actionOf(item.status) ?: return
            Spacer(Modifier.height(12.dp))
            when (action) {
                AppAction.Install -> Button(onClick = { onInstall(item.app) }) { Text("Zainstaluj") }
                AppAction.Update -> Button(onClick = { onInstall(item.app) }) { Text("Aktualizuj") }
                AppAction.Open -> FilledTonalButton(onClick = { onOpen(item.app) }) { Text("Otwórz") }
            }
        }
    }
}

@Composable
private fun Progress(label: String, indicator: @Composable () -> Unit) {
    Spacer(Modifier.height(12.dp))
    indicator()
    Spacer(Modifier.height(4.dp))
    Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

private fun failureText(reason: FailureReason) = when (reason) {
    FailureReason.Network -> "Nie udało się pobrać. Sprawdź połączenie z internetem."
    FailureReason.Corrupt -> "Pobrany plik jest uszkodzony. Spróbuj ponownie."
    FailureReason.SignatureConflict ->
        "Zainstalowana wersja pochodzi z innego źródła. Odinstaluj ją, żeby zainstalować tę (jej dane zostaną usunięte)."
    FailureReason.Storage -> "Za mało miejsca na telefonie."
    FailureReason.Incompatible -> "Ta aplikacja nie działa na tym telefonie."
    FailureReason.Other -> "Instalacja nie powiodła się."
}
