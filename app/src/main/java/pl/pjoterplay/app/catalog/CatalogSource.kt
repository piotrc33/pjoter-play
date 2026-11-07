package pl.pjoterplay.app.catalog

import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/** GitHub Pages of the public pjoter-play repo (CLAUDE.md, Layout). */
const val CATALOG_URL = "https://piotrc33.github.io/pjoter-play/apps.json"

private const val TIMEOUT_MS = 15_000

suspend fun fetchCatalog(): Catalog = withContext(Dispatchers.IO) {
    parseCatalog(download(URL(CATALOG_URL)).decodeToString())
}

fun iconUrl(app: CatalogApp): String = URL(URL(CATALOG_URL), app.icon).toString()

// A handful of 192 px PNGs: without the cache every pull-to-refresh would download them again.
private val icons = LruCache<String, ImageBitmap>(32)

/** Null when the icon can't be downloaded: the list shows a placeholder instead. */
suspend fun loadIcon(url: String): ImageBitmap? {
    icons.get(url)?.let { return it }
    return withContext(Dispatchers.IO) {
        runCatching {
            val bytes = download(URL(url))
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
        }.getOrNull()
    }?.also { icons.put(url, it) }
}

private fun download(url: URL): ByteArray {
    val connection = url.openConnection() as HttpURLConnection
    try {
        connection.connectTimeout = TIMEOUT_MS
        connection.readTimeout = TIMEOUT_MS
        // A fresh release should show up on the next refresh, not after the HTTP cache expires.
        connection.useCaches = false
        connection.setRequestProperty("Cache-Control", "no-cache")
        if (connection.responseCode != HttpURLConnection.HTTP_OK) {
            throw java.io.IOException("HTTP ${connection.responseCode} for $url")
        }
        return connection.inputStream.use { it.readBytes() }
    } finally {
        connection.disconnect()
    }
}
