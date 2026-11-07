package pl.pjoterplay.app

import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.pm.PackageInfoCompat
import pl.pjoterplay.app.catalog.CatalogApp
import pl.pjoterplay.app.catalog.Release
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

data class InstalledVersion(val code: Long, val name: String)

sealed interface AppStatus {
    /** In the catalog, but without a release yet. */
    data object NotReleased : AppStatus

    data class Unsupported(val minSdk: Int) : AppStatus

    data class NotInstalled(val release: Release) : AppStatus

    data class UpToDate(val installed: InstalledVersion) : AppStatus

    data class UpdateAvailable(val installed: InstalledVersion, val release: Release) : AppStatus
}

fun statusOf(app: CatalogApp, installed: InstalledVersion?, sdkInt: Int): AppStatus {
    val release = app.release
    // An installed app is supported by definition, even if the catalog later raises its minSdk.
    if (installed != null) {
        return if (release != null && release.versionCode > installed.code) {
            AppStatus.UpdateAvailable(installed, release)
        } else {
            AppStatus.UpToDate(installed)
        }
    }
    return when {
        sdkInt < app.minSdk -> AppStatus.Unsupported(app.minSdk)
        release == null -> AppStatus.NotReleased
        else -> AppStatus.NotInstalled(release)
    }
}

fun PackageManager.installedVersion(packageName: String): InstalledVersion? = try {
    val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0))
    } else {
        @Suppress("DEPRECATION")
        getPackageInfo(packageName, 0)
    }
    InstalledVersion(PackageInfoCompat.getLongVersionCode(info), info.versionName.orEmpty())
} catch (_: PackageManager.NameNotFoundException) {
    null
}

// The name people know from their phone's settings. Keep in sync with ANDROID_VERSIONS in docs/app.js.
private val androidVersions = mapOf(
    26 to "8.0", 27 to "8.1", 28 to "9", 29 to "10", 30 to "11", 31 to "12", 32 to "12L",
    33 to "13", 34 to "14", 35 to "15", 36 to "16", 37 to "17",
)

fun androidVersionName(sdk: Int): String = androidVersions[sdk] ?: "API $sdk"

// Keep in sync with megabytes() in docs/app.js.
fun megabytes(bytes: Long): String {
    val format = DecimalFormat("0.#", DecimalFormatSymbols(Locale.forLanguageTag("pl-PL")))
    return "${format.format(bytes / 1_000_000.0)} MB"
}

enum class AppAction { Install, Update, Open }

/** The card's button; none when there is nothing to do on this phone. */
fun actionOf(status: AppStatus): AppAction? = when (status) {
    is AppStatus.NotInstalled -> AppAction.Install
    is AppStatus.UpdateAvailable -> AppAction.Update
    is AppStatus.UpToDate -> AppAction.Open
    AppStatus.NotReleased, is AppStatus.Unsupported -> null
}
