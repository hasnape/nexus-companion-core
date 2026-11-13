package fr.nexuscompanion.android

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PortableBrainTest {
    private val context get() = RuntimeEnvironment.getApplication()
    private fun library(id: String, cipher: KnowledgeCipher = LocalKnowledgeLibraryTest.JvmCipher(id)) = LocalKnowledgeLibrary(context, id, cipher)
    private fun secret() = "une phrase de sauvegarde 2026".toCharArray()
    private fun file(id: String) = File(context.filesDir, "knowledge-$id.json")

    @Test fun portableRoundTripPreservesAllNotesAndHistoryAcrossStorageNamespaces() {
        val source = library("portable-source")
        val original = source.addUserNote("Métier", "Je travaille comme technicien informatique.").entry
        val edited = source.replace(original, "Métier", "Je travaille comme formateur informatique.").entry
        source.addApprovedSource("Volcan", "Un volcan peut libérer de la lave.", "external:fixture")
        val before = source.portableSnapshot()
        val encrypted = PortableBrain.encrypt(before, secret())
        assertArrayEquals(before, source.portableSnapshot())
        assertFalse(String(encrypted).contains("formateur"))
        assertFalse(encrypted.contentEquals(PortableBrain.encrypt(before, secret())))
        val restored = library("portable-other-device")
        val decoded = PortableBrain.decrypt(encrypted, secret())
        val summary = restored.inspectPortable(decoded)
        assertEquals(2, summary.activeNotes); assertEquals(1, summary.archivedNotes)
        restored.restorePortable(decoded, restored.portableFingerprint())
        assertEquals(source.list(), restored.list())
        assertEquals(source.history(edited.id), restored.history(edited.id))
        assertArrayEquals(before, restored.portableSnapshot())
        assertFalse(file("portable-source").readBytes().contentEquals(file("portable-other-device").readBytes()))
    }
    @Test fun wrongSecretTamperingAndUnboundedKdfParametersCannotOpenAnArchive() {
        val data = library("portable-auth").portableSnapshot()
        val blob = PortableBrain.encrypt(data, secret())
        assertThrows(Exception::class.java) { PortableBrain.decrypt(blob, "une autre phrase incorrecte".toCharArray()) }
        val changed = blob.copyOf().also { it[it.lastIndex] = (it.last().toInt() xor 1).toByte() }
        assertThrows(Exception::class.java) { PortableBrain.decrypt(changed, secret()) }
        val kdf = blob.copyOf().also { it[4] = 127 }
        assertThrows(IllegalArgumentException::class.java) { PortableBrain.decrypt(kdf, secret()) }
        assertThrows(IllegalArgumentException::class.java) { PortableBrain.decrypt(byteArrayOf(1, 2), secret()) }
        assertThrows(IllegalArgumentException::class.java) { PortableBrain.encrypt(data, "123456".toCharArray()) }
    }
    @Test fun restoreKeepsAnEncryptedUndoPointAndUndoRestoresThePreviousContent() {
        val old = library("portable-undo")
        old.addUserNote("Avant", "Une information personnelle avant la restauration.")
        val original = old.portableSnapshot()
        val incoming = library("portable-import").apply { addUserNote("Après", "Une information différente dans la sauvegarde.") }.portableSnapshot()
        old.restorePortable(incoming, old.portableFingerprint())
        assertTrue(old.canUndoRestore()); assertTrue(old.list().single().title == "Après")
        val journal = File(context.noBackupFilesDir, "knowledge-rescue-portable-undo.bin").readBytes()
        assertFalse(String(journal).contains("personnelle"))
        old.undoRestore()
        assertArrayEquals(original, old.portableSnapshot()); assertFalse(old.canUndoRestore())
    }
    @Test fun aChangedTargetOrLaterUserEditCannotBeSilentlyOverwritten() {
        val store = library("portable-conflict")
        store.addUserNote("Avant", "Une information avant la vérification.")
        val expected = store.portableFingerprint()
        val incoming = library("portable-empty").portableSnapshot()
        store.addUserNote("Nouvelle", "Une information ajoutée après la vérification.")
        val current = file("portable-conflict").readBytes()
        assertThrows(KnowledgeChanged::class.java) { store.restorePortable(incoming, expected) }
        assertArrayEquals(current, file("portable-conflict").readBytes())
        store.restorePortable(incoming, store.portableFingerprint())
        store.addUserNote("Après", "Une note créée après restauration.")
        assertFalse(store.canUndoRestore())
        assertThrows(KnowledgeChanged::class.java) { store.undoRestore() }
        assertEquals("Après", store.list().single().title)
    }
    @Test fun failureOrSessionRevocationKeepsTheExistingMemoryAndUndoPoint() {
        val id = "portable-failure"; val delegate = LocalKnowledgeLibraryTest.JvmCipher(id); var fail = false
        val cipher = object : KnowledgeCipher {
            override fun isEncrypted(bytes: ByteArray) = delegate.isEncrypted(bytes)
            override fun decrypt(blob: ByteArray) = delegate.decrypt(blob)
            override fun encrypt(plaintext: ByteArray): ByteArray {
                if (fail && KnowledgePayload.decode(plaintext).firstOrNull() == '{'.code.toByte()) throw IllegalStateException("write refused")
                return delegate.encrypt(plaintext)
            }
        }
        val store = library(id, cipher).apply { addUserNote("Avant", "Donnée originale conservée.") }
        val incoming = library("portable-new").apply { addUserNote("Après", "Donnée de la nouvelle archive.") }.portableSnapshot()
        val original = store.portableSnapshot()
        var checks = 0
        assertThrows(IllegalStateException::class.java) { store.restorePortable(incoming, store.portableFingerprint()) { if (++checks == 2) error("session closed") } }
        assertArrayEquals(original, store.portableSnapshot())
        // Fail before the atomic main write, after its recovery journal has been staged.
        fail = true
        assertThrows(IllegalStateException::class.java) { store.restorePortable(incoming, store.portableFingerprint()) }
        fail = false
        assertArrayEquals(original, store.portableSnapshot())
        assertFalse(store.canUndoRestore())
    }
    @Test fun invalidHistoryMetadataAndTrailingPayloadAreRejectedBeforeAnyWrite() {
        val store = library("portable-invalid")
        val first = store.addUserNote("Une note", "Le contenu de la note est conservé.").entry
        val bytes = file("portable-invalid").readBytes()
        val root = JSONObject(String(store.portableSnapshot()))
        root.getJSONArray("entries").getJSONObject(0).put("supersededBy", first.id)
        assertThrows(IllegalArgumentException::class.java) { store.restorePortable(root.toString().toByteArray(), store.portableFingerprint()) }
        val verified = JSONObject(String(store.portableSnapshot())).also { it.getJSONArray("entries").getJSONObject(0).put("verified", true) }
        assertThrows(IllegalArgumentException::class.java) { store.inspectPortable(verified.toString().toByteArray()) }
        assertThrows(IllegalArgumentException::class.java) { store.inspectPortable(store.portableSnapshot() + " trailing".toByteArray()) }
        assertArrayEquals(bytes, file("portable-invalid").readBytes())
    }
}
