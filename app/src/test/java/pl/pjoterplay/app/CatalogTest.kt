package pl.pjoterplay.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.pjoterplay.app.catalog.MANAGER_ID
import pl.pjoterplay.app.catalog.UnsupportedCatalogException
import pl.pjoterplay.app.catalog.parseCatalog
import java.io.File

class CatalogTest {
    // The published catalog itself, so a change to docs/apps.json that the manager can't read fails here.
    // Unit tests run in app/.
    private val docs = File("../docs")

    @Test
    fun parsesThePublishedCatalog() {
        val catalog = parseCatalog(File(docs, "apps.json").readText())
        val ids = catalog.apps.map { it.id }
        assertTrue(MANAGER_ID in ids)
        assertTrue("pl.reader.app" in ids)
        assertEquals("ids must be unique", ids.size, ids.toSet().size)
        for (app in catalog.apps) {
            assertTrue("${app.id}: icon ${app.icon} missing in docs/", File(docs, app.icon).isFile)
            assertTrue("${app.id}: a PNG, Android has no built-in SVG", app.icon.endsWith(".png"))
            assertTrue("${app.id}: minSdk below the manager's", app.minSdk >= 26)
        }
    }

    @Test
    fun ignoresFieldsAddedLater() {
        val catalog = parseCatalog(
            """{"schema": 1, "future": true, "apps": [{"id": "a", "name": "A", "summary": "", "description": "",
              "icon": "a.png", "minSdk": 26, "release": null, "screenshots": []}]}""",
        )
        assertEquals("a", catalog.apps.single().id)
    }

    @Test(expected = UnsupportedCatalogException::class)
    fun rejectsANewerSchema() {
        parseCatalog("""{"schema": 2, "apps": []}""")
    }
}
