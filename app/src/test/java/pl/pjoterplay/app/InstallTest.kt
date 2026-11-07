package pl.pjoterplay.app

import android.content.pm.PackageInstaller
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.pjoterplay.app.catalog.Release
import pl.pjoterplay.app.install.matches
import java.security.MessageDigest

class InstallTest {
    private val apk = "not really an apk".toByteArray()
    private val sha = MessageDigest.getInstance("SHA-256").digest(apk)
    private val hex = sha.joinToString("") { "%02x".format(it) }
    private fun release(sha256: String = hex, size: Long = apk.size.toLong()) =
        Release(versionCode = 1, versionName = "1", apk = "https://x/a.apk", sha256 = sha256, size = size)

    @Test
    fun downloadMustMatchTheCatalog() {
        assertTrue(matches(release(), sha, apk.size.toLong()))
        assertTrue("hex case doesn't matter", matches(release(sha256 = hex.uppercase()), sha, apk.size.toLong()))
        assertFalse("another file", matches(release(sha256 = "0".repeat(64)), sha, apk.size.toLong()))
        assertFalse("cut short", matches(release(), sha, apk.size - 1L))
    }

    @Test
    fun buttonFollowsTheStatus() {
        val installed = InstalledVersion(1, "1")
        assertEquals(AppAction.Install, actionOf(AppStatus.NotInstalled(release())))
        assertEquals(AppAction.Update, actionOf(AppStatus.UpdateAvailable(installed, release())))
        assertEquals(AppAction.Open, actionOf(AppStatus.UpToDate(installed)))
        assertNull(actionOf(AppStatus.NotReleased))
        assertNull(actionOf(AppStatus.Unsupported(34)))
    }

    @Test
    fun installerStatusesTheUserCanActOn() {
        assertEquals(FailureReason.SignatureConflict, reasonOf(PackageInstaller.STATUS_FAILURE_CONFLICT))
        assertEquals(FailureReason.Storage, reasonOf(PackageInstaller.STATUS_FAILURE_STORAGE))
        assertEquals(FailureReason.Other, reasonOf(PackageInstaller.STATUS_FAILURE))
    }
}
