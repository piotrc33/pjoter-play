package pl.pjoterplay.app

import org.junit.Assert.assertEquals
import org.junit.Test
import pl.pjoterplay.app.catalog.CatalogApp
import pl.pjoterplay.app.catalog.Release

class AppStatusTest {
    private val release = Release(versionCode = 5, versionName = "1.5", apk = "https://x/a.apk", sha256 = "00", size = 1)
    private fun app(release: Release? = this.release, minSdk: Int = 34) =
        CatalogApp("a", "A", "", "", "a.png", minSdk, release)

    @Test
    fun olderInstalledVersionHasAnUpdate() {
        val installed = InstalledVersion(4, "1.4")
        assertEquals(AppStatus.UpdateAvailable(installed, release), statusOf(app(), installed, 36))
    }

    @Test
    fun sameOrNewerInstalledVersionIsUpToDate() {
        // Newer: a debug or locally built version, which the manager must not "update" back down.
        for (code in listOf(5L, 6L)) {
            val installed = InstalledVersion(code, "x")
            assertEquals(AppStatus.UpToDate(installed), statusOf(app(), installed, 36))
        }
    }

    @Test
    fun installedWithoutAReleaseIsUpToDate() {
        val installed = InstalledVersion(1, "0.1")
        assertEquals(AppStatus.UpToDate(installed), statusOf(app(release = null), installed, 36))
    }

    @Test
    fun installedOnAnOldAndroidIsStillShownAsInstalled() {
        val installed = InstalledVersion(5, "1.5")
        assertEquals(AppStatus.UpToDate(installed), statusOf(app(minSdk = 34), installed, 30))
    }

    @Test
    fun notInstalled() {
        assertEquals(AppStatus.NotInstalled(release), statusOf(app(), null, 34))
        assertEquals(AppStatus.Unsupported(34), statusOf(app(), null, 33))
        assertEquals(AppStatus.NotReleased, statusOf(app(release = null), null, 36))
        // Unsupported wins: "coming soon" would promise something this phone will never get.
        assertEquals(AppStatus.Unsupported(34), statusOf(app(release = null), null, 30))
    }

    @Test
    fun androidVersionNames() {
        assertEquals("8.0", androidVersionName(26))
        assertEquals("14", androidVersionName(34))
        assertEquals("API 99", androidVersionName(99))
    }
}
