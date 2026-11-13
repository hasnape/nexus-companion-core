package fr.nexuscompanion.android

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowAlertDialog
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

class BackupActivityFixture : BrainBackupActivity() {
    override fun createLibrary() = LocalKnowledgeLibrary(applicationContext, "backup-ui-fixture", LocalKnowledgeLibraryTest.JvmCipher("backup-ui-fixture"))
    fun deliverResult(requestCode: Int, resultCode: Int, data: Intent?) = onActivityResult(requestCode, resultCode, data)
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class BrainBackupActivityTest {
    private lateinit var life: org.robolectric.android.controller.ActivityController<BackupActivityFixture>
    private lateinit var activity: BackupActivityFixture
    @Before fun open() { UserAccessSession.lock(); life = Robolectric.buildActivity(BackupActivityFixture::class.java).setup(); activity = life.get() }
    @After fun close() { life.pause().stop().destroy(); UserAccessSession.lock() }
    private fun views(view: View = activity.findViewById(android.R.id.content)): List<View> =
        listOf(view) + if (view is ViewGroup) (0 until view.childCount).flatMap { views(view.getChildAt(it)) } else emptyList()
    private fun button(label: Int) = views().filterIsInstance<Button>().single { it.text.toString() == activity.getString(label) }
    private fun fields() = views().filterIsInstance<EditText>()
    private fun unlock() { UserAccessSession.unlock(); activity.deliverResult(470, Activity.RESULT_OK, Intent()) }
    private fun await(condition: () -> Boolean) {
        val end = System.nanoTime() + 10_000_000_000L
        while (System.nanoTime() < end) {
            shadowOf(Looper.getMainLooper()).idle()
            if (condition()) return
            Thread.sleep(10)
        }
        fail("Backup flow did not complete")
    }
    @Test fun privateExportIsClosedByDefaultAndUsesSecureUnsavedFields() {
        assertFalse(button(R.string.backup_export).isEnabled)
        assertFalse(button(R.string.backup_restore).isEnabled)
        assertTrue(activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0)
        assertTrue(fields().all { !it.isSaveEnabled && !it.isEnabled })
    }
    @Test fun leavingTheScreenRevokesTheSessionAndErasesTheEnteredPhrase() {
        unlock(); fields().forEach { it.setText("une phrase privée confidentielle") }
        assertTrue(button(R.string.backup_export).isEnabled)
        life.pause()
        assertFalse(UserAccessSession.allowed()); assertTrue(fields().all { it.text.isEmpty() })
        life.resume()
        assertFalse(button(R.string.backup_export).isEnabled)
    }
    @Test fun exportWritesOnlyAnAuthenticatedArchiveToTheExplicitlyChosenDocument() {
        unlock()
        activity.createLibrary().addUserNote("Repère", "Cette mémoire privée doit rester chiffrée.")
        fields().forEach { it.setText("une phrase de sauvegarde 2026") }
        button(R.string.backup_export).performClick()
        var started: Intent? = null
        await { started = shadowOf(activity).nextStartedActivity; started != null }
        assertEquals(Intent.ACTION_CREATE_DOCUMENT, started!!.action)
        val uri = Uri.parse("content://backup-fixture/archive.nxb")
        val bytes = ByteArrayOutputStream()
        shadowOf(activity.contentResolver).registerOutputStream(uri, bytes)
        life.pause(); life.resume()
        activity.deliverResult(471, Activity.RESULT_OK, Intent().setData(uri))
        await { views().filterIsInstance<TextView>().any { it.text.toString() == activity.getString(R.string.backup_exported) } }
        val archive = bytes.toByteArray()
        assertFalse(String(archive).contains("mémoire privée"))
        val plain = PortableBrain.decrypt(archive, "une phrase de sauvegarde 2026".toCharArray())
        assertEquals(1, JSONObject(String(plain)).getJSONArray("entries").length())
        assertTrue(fields().all { it.text.isEmpty() }); assertFalse(UserAccessSession.allowed())
    }
    @Test fun restoreRequiresADecryptedPreviewAndConfirmationAndCanBeUndone() {
        val library = activity.createLibrary()
        val original = library.addUserNote("Avant", "La mémoire existante doit pouvoir être rétablie.").entry
        val source = LocalKnowledgeLibrary(activity, "backup-ui-source", LocalKnowledgeLibraryTest.JvmCipher("backup-ui-source"))
        source.addUserNote("Importée", "Cette note vient de la sauvegarde portable.")
        val archive = PortableBrain.encrypt(source.portableSnapshot(), "une phrase de sauvegarde 2026".toCharArray())
        val uri = Uri.parse("content://backup-fixture/import.nxb")
        shadowOf(activity.contentResolver).registerInputStream(uri, ByteArrayInputStream(archive))
        activity.deliverResult(472, Activity.RESULT_OK, Intent().setData(uri))
        await { views().filterIsInstance<TextView>().any { it.text.toString() == activity.getString(R.string.backup_selected) } }
        assertEquals(listOf(original), library.list())
        assertFalse(button(R.string.backup_restore).isEnabled)
        unlock(); fields().first().setText("une phrase de sauvegarde 2026")
        button(R.string.backup_preview).performClick()
        await { button(R.string.backup_restore).isEnabled }
        assertEquals(listOf(original), library.list())
        button(R.string.backup_restore).performClick()
        val dialog = ShadowAlertDialog.getLatestAlertDialog()
        assertNotNull(dialog)
        assertEquals(listOf(original), library.list())
        dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).performClick()
        await { views().filterIsInstance<TextView>().any { it.text.toString() == activity.getString(R.string.backup_restored) } }
        assertEquals("Importée", library.list().single().title)
        button(R.string.backup_undo).performClick()
        await { views().filterIsInstance<TextView>().any { it.text.toString() == activity.getString(R.string.backup_undo_done) } }
        assertEquals(listOf(original), library.list())
    }
}
