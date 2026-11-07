package pl.pjoterplay.app.install

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import pl.pjoterplay.app.catalog.Release
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

class CorruptDownloadException : IOException("The APK's SHA-256 or size differs from the catalog's")

private const val TIMEOUT_MS = 30_000

/**
 * Downloads [release]'s APK into the app's cache, checking it against the catalog as it arrives: a broken or
 * tampered file never reaches the system installer. [onProgress] gets 0..1.
 */
suspend fun download(context: Context, appId: String, release: Release, onProgress: (Float) -> Unit): File =
    withContext(Dispatchers.IO) {
        val file = File(context.cacheDir, "apk/$appId.apk").apply { parentFile?.mkdirs() }
        // GitHub answers a release asset with a redirect to its storage, which HttpURLConnection follows (both https).
        val connection = URL(release.apk).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                throw IOException("HTTP ${connection.responseCode} for ${release.apk}")
            }
            val digest = MessageDigest.getInstance("SHA-256")
            var received = 0L
            connection.inputStream.use { input ->
                file.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        digest.update(buffer, 0, read)
                        output.write(buffer, 0, read)
                        received += read
                        onProgress((received.toFloat() / release.size).coerceAtMost(1f))
                    }
                }
            }
            if (!matches(release, digest.digest(), received)) {
                file.delete()
                throw CorruptDownloadException()
            }
            file
        } finally {
            connection.disconnect()
        }
    }

fun matches(release: Release, sha256: ByteArray, size: Long): Boolean =
    size == release.size && sha256.joinToString("") { "%02x".format(it) }.equals(release.sha256, ignoreCase = true)

/** Hands [apk] to the system installer; the outcome arrives in [InstallReceiver]. */
suspend fun install(context: Context, appId: String, apk: File) = withContext(Dispatchers.IO) {
    val installer = context.packageManager.packageInstaller
    val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
        setAppPackageName(appId)
        setSize(apk.length())
        // Android 12+: an update of an app this manager installed goes through without the system's dialog.
        // The first install of each app (and below Android 12 every one) still asks the user.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
        }
    }
    val sessionId = installer.createSession(params)
    installer.openSession(sessionId).use { session ->
        session.openWrite("base.apk", 0, apk.length()).use { output ->
            apk.inputStream().use { it.copyTo(output) }
            session.fsync(output)
        }
        val intent = Intent(context, InstallReceiver::class.java).putExtra(InstallReceiver.EXTRA_APP_ID, appId)
        // Mutable: the system adds the status extras to it. Explicit, so that is allowed on Android 12+.
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or
            (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0)
        session.commit(PendingIntent.getBroadcast(context, sessionId, intent, flags).intentSender)
    }
    // The session holds its own copy now.
    apk.delete()
}
