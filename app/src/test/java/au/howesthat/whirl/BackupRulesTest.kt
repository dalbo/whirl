package au.howesthat.whirl

import au.howesthat.whirl.core.data.ACCOUNT_STORE
import au.howesthat.whirl.core.data.DATASTORE_FILES
import au.howesthat.whirl.core.data.LIBRARY_STORE
import au.howesthat.whirl.core.data.SETTINGS_STORE
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * What Android backs up (docs/plans/settings-backup.md). The API key must never go to the cloud unencrypted, and the
 * cache (whirl.db) and shared prefs never at all. Once a rules file has an include, only what it includes is backed up.
 */
class BackupRulesTest {
    private fun rules(path: String): Element =
        DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(File("src/main/res/$path")).documentElement

    /** Each include under [parent] as (path, requireFlags or ""). */
    private fun includes(parent: Element): List<Pair<String, String>> {
        val nodes = parent.getElementsByTagName("include")
        return (0 until nodes.length).map { nodes.item(it) as Element }.map {
            assertEquals("file", it.getAttribute("domain"))
            it.getAttribute("path") to it.getAttribute("requireFlags")
        }
    }

    private fun file(store: String) = "datastore/$store.preferences_pb"
    private val settings = file(SETTINGS_STORE)
    private val library = file(LIBRARY_STORE)
    private val account = file(ACCOUNT_STORE)

    @Test
    fun `every DataStore file is one the rules know`() {
        assertEquals(setOf(settings, library, account), DATASTORE_FILES.map(::file).toSet(), "a new store: classify it here and in the rules")
    }

    @Test
    fun `Android 8 backs up settings and favourites, never the key`() {
        val root = rules("xml/backup_rules.xml")
        assertEquals(setOf(settings to "", library to ""), includes(root).toSet())
        assertEquals(0, root.getElementsByTagName("exclude").length)
    }

    @Test
    fun `Android 9 to 11 back up the key only encrypted or phone to phone`() {
        assertEquals(
            setOf(settings to "", library to "", account to "clientSideEncryption", account to "deviceToDeviceTransfer"),
            includes(rules("xml-v28/backup_rules.xml")).toSet(),
        )
    }

    @Test
    fun `Android 12+ backs up to the cloud only when it is end-to-end encrypted`() {
        val root = rules("xml/data_extraction_rules.xml")
        val cloud = root.getElementsByTagName("cloud-backup").item(0) as Element
        val transfer = root.getElementsByTagName("device-transfer").item(0) as Element
        // requireFlags does not exist in this format: the switch is on the whole of cloud backup.
        assertEquals("true", cloud.getAttribute("disableIfNoEncryptionCapabilities"))
        assertEquals(setOf(settings to "", library to "", account to ""), includes(cloud).toSet())
        assertEquals(setOf(settings to "", library to "", account to ""), includes(transfer).toSet())
        val all = root.getElementsByTagName("*")
        assertTrue((0 until all.length).none { (all.item(it) as Element).hasAttribute("requireFlags") })
    }
}
