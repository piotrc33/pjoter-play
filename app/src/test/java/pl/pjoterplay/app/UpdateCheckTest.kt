package pl.pjoterplay.app

import org.junit.Assert.assertEquals
import org.junit.Test
import pl.pjoterplay.app.catalog.Catalog
import pl.pjoterplay.app.catalog.CatalogApp
import pl.pjoterplay.app.catalog.Release
import pl.pjoterplay.app.updates.announcementKeys
import pl.pjoterplay.app.updates.availableUpdates
import pl.pjoterplay.app.updates.notificationText
import pl.pjoterplay.app.updates.notificationTitle

class UpdateCheckTest {
    private fun app(id: String, name: String, code: Long?) = CatalogApp(
        id, name, "", "", "$id.png", 26,
        code?.let { Release(it, "0.$it", "https://x/$id.apk", "00", 1) },
    )

    private val catalog = Catalog(
        1,
        listOf(app("a", "Alfa", 3), app("b", "Beta", 2), app("c", "Gamma", 5), app("d", "Delta", null)),
    )

    @Test
    fun onlyInstalledAppsWithANewerRelease() {
        val installed = mapOf("a" to 2L, "b" to 2L, "d" to 1L)
        val updates = availableUpdates(catalog, { id -> installed[id]?.let { InstalledVersion(it, "x") } }, 36)
        // c isn't installed, b is up to date, d has no release.
        assertEquals(listOf("a"), updates.map { it.id })
        assertEquals(setOf("a:3"), announcementKeys(updates))
    }

    @Test
    fun notificationTexts() {
        val one = listOf(catalog.apps[0])
        assertEquals("Dostępna aktualizacja", notificationTitle(one))
        assertEquals("Alfa 0.3", notificationText(one))
        val two = catalog.apps.take(2)
        assertEquals("Dostępne aktualizacje (2)", notificationTitle(two))
        assertEquals("Alfa 0.3, Beta 0.2", notificationText(two))
    }
}
