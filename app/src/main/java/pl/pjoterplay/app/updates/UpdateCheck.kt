package pl.pjoterplay.app.updates

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import pl.pjoterplay.app.AppStatus
import pl.pjoterplay.app.InstalledVersion
import pl.pjoterplay.app.MainActivity
import pl.pjoterplay.app.R
import pl.pjoterplay.app.catalog.Catalog
import pl.pjoterplay.app.catalog.CatalogApp
import pl.pjoterplay.app.catalog.UnsupportedCatalogException
import pl.pjoterplay.app.catalog.fetchCatalog
import pl.pjoterplay.app.installedVersion
import pl.pjoterplay.app.statusOf
import java.util.concurrent.TimeUnit

/** The catalog's apps on this phone that have a newer release, the release manager included. */
fun availableUpdates(catalog: Catalog, installed: (String) -> InstalledVersion?, sdkInt: Int): List<CatalogApp> =
    catalog.apps.filter { statusOf(it, installed(it.id), sdkInt) is AppStatus.UpdateAvailable }

/** One per release ("id:versionCode"), so that a release is announced once, not every day until it is installed. */
fun announcementKeys(updates: List<CatalogApp>): Set<String> =
    updates.mapNotNull { app -> app.release?.let { "${app.id}:${it.versionCode}" } }.toSet()

fun notificationTitle(updates: List<CatalogApp>): String =
    if (updates.size == 1) "Dostępna aktualizacja" else "Dostępne aktualizacje (${updates.size})"

fun notificationText(updates: List<CatalogApp>): String =
    updates.joinToString(", ") { app -> "${app.name} ${app.release?.versionName.orEmpty()}" }

class UpdateCheckWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val catalog = try {
            fetchCatalog()
        } catch (e: UnsupportedCatalogException) {
            // Only a newer manager reads this catalog: retrying wouldn't help, the next day's check may.
            Log.w(TAG, "Catalog too new", e)
            return Result.success()
        } catch (e: Exception) {
            Log.w(TAG, "Catalog download failed", e)
            return Result.retry()
        }
        val context = applicationContext
        val updates = availableUpdates(catalog, context.packageManager::installedVersion, Build.VERSION.SDK_INT)
        val keys = announcementKeys(updates)
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val announced = prefs.getStringSet(ANNOUNCED, emptySet()).orEmpty()
        Log.i(TAG, "Updates: $keys, announced before: $announced")
        when {
            keys.isEmpty() -> NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
            // The notification lists all of them, also the ones announced before.
            (keys - announced).isNotEmpty() -> notify(context, updates)
        }
        // Only the current ones: installed updates drop out of the set.
        prefs.edit { putStringSet(ANNOUNCED, keys) }
        return Result.success()
    }

    private fun notify(context: Context, updates: List<CatalogApp>) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL, "Aktualizacje", NotificationManager.IMPORTANCE_DEFAULT),
        )
        val open = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(notificationTitle(updates))
            .setContentText(notificationText(updates))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
    }

    companion object {
        private const val TAG = "UpdateCheck"
        private const val PREFS = "updates"
        private const val ANNOUNCED = "announced"
        private const val CHANNEL = "updates"
        const val NOTIFICATION_ID = 1
        private const val WORK = "update-check"

        /** Daily while there is a network; KEEP: a restart of the app mustn't push the next check back a day. */
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<UpdateCheckWorker>(1, TimeUnit.DAYS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(WORK, ExistingPeriodicWorkPolicy.KEEP, request)
        }

        /** Right now, once: for trying the check out on the debug build (CLAUDE.md, Workflow). */
        fun runOnce(context: Context) {
            WorkManager.getInstance(context).enqueue(OneTimeWorkRequestBuilder<UpdateCheckWorker>().build())
        }
    }
}
