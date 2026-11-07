package pl.pjoterplay.app.catalog

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

// docs/apps.json. Keep in sync with its contract in CLAUDE.md and with docs/app.js.

/** The newest `schema` this manager understands; a higher one means the format changed incompatibly. */
const val SUPPORTED_SCHEMA = 1

/** The manager's own entry: it updates itself, but doesn't list itself. Keep in sync with MANAGER_ID in docs/app.js. */
const val MANAGER_ID = "pl.pjoterplay.app"

@Serializable
data class Catalog(val schema: Int, val apps: List<CatalogApp>)

@Serializable
data class CatalogApp(
    val id: String,
    val name: String,
    val summary: String,
    val description: String,
    /** Relative to the catalog's URL. */
    val icon: String,
    /** API level. */
    val minSdk: Int,
    /** Null until the app's first release. */
    val release: Release? = null,
)

@Serializable
data class Release(
    val versionCode: Long,
    val versionName: String,
    val apk: String,
    /** Hex. */
    val sha256: String,
    /** Bytes. */
    val size: Long,
)

class UnsupportedCatalogException(schema: Int) :
    Exception("Catalog schema $schema, this manager knows up to $SUPPORTED_SCHEMA")

// A field the page adds later must not break the managers already on people's phones.
private val json = Json { ignoreUnknownKeys = true }

fun parseCatalog(text: String): Catalog {
    val catalog = json.decodeFromString<Catalog>(text)
    if (catalog.schema > SUPPORTED_SCHEMA) throw UnsupportedCatalogException(catalog.schema)
    return catalog
}
