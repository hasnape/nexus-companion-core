package fr.nexuscompanion.android

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class LocalKnowledgeLibraryTest {
    /** Real AES-GCM for repository tests; Android Keystore remains the production provider. */
    internal class JvmCipher(private val namespace: String) : KnowledgeCipher {
        private val key = SecretKeySpec(ByteArray(32) { 42 }, "AES")
        private val magic = byteArrayOf(0x4e, 0x58, 0x4b, 0x01)
        override fun isEncrypted(bytes: ByteArray) = bytes.size >= 4 && bytes.take(4) == magic.toList()
        override fun encrypt(plaintext: ByteArray): ByteArray {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, key); cipher.updateAAD(namespace.toByteArray())
            return magic + cipher.iv + cipher.doFinal(plaintext)
        }
        override fun decrypt(blob: ByteArray): ByteArray {
            require(isEncrypted(blob))
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, blob.copyOfRange(4, 16)))
            cipher.updateAAD(namespace.toByteArray())
            return cipher.doFinal(blob.copyOfRange(16, blob.size))
        }
    }
    private val context: Context get() = RuntimeEnvironment.getApplication()
    private fun file(id: String = "test") = File(context.filesDir, "knowledge-$id.json")
    private fun library(id: String = "test") = LocalKnowledgeLibrary(context, id, JvmCipher(id))
    private fun entry(id: String, text: String, expiresAt: Long? = null) = JSONObject()
        .put("id", id).put("title", text).put("excerpt", text).put("source", "user:manual")
        .put("recordedAt", 100L).put("expiresAt", expiresAt ?: JSONObject.NULL).put("verified", false)
    private fun legacy(vararg entries: JSONObject) = JSONObject().put("schema", 1)
        .put("entries", JSONArray(entries.toList())).toString()

    @Test fun savesEncryptedNotesAndReadsThemAcrossLibraryInstances() {
        library().addUserNote("Thé vert", "Je préfère le thé vert")
        val bytes = file().readBytes()
        assertFalse(bytes.toString(Charsets.UTF_8).contains("thé vert"))
        assertTrue(JvmCipher("test").isEncrypted(bytes))
        assertEquals("Je préfère le thé vert", library().search("the").single().excerpt)
    }

    @Test fun migratesKnownLegacyPlaintextAtomicallyBeforeReturningIt() {
        file().writeText(legacy(entry("one", "ancienne note")))
        assertEquals("ancienne note", library().list().single().excerpt)
        assertTrue(JvmCipher("test").isEncrypted(file().readBytes()))
        assertEquals("ancienne note", library().list().single().excerpt)
    }

    @Test fun refusesTamperedCiphertextAndNeverOverwritesItWithANewNote() {
        library().addUserNote("Note", "Première note")
        val bytes = file().readBytes(); bytes[bytes.lastIndex] = (bytes.last().toInt() xor 1).toByte()
        file().writeBytes(bytes)
        assertThrows(Exception::class.java) { library().addUserNote("Autre", "Seconde note") }
        assertArrayEquals(bytes, file().readBytes())
    }

    @Test fun doesNotInterpretOrOverwriteUnknownLegacySchema() {
        val original = "{\"schema\":99,\"entries\":[]}".toByteArray()
        file().writeBytes(original)
        assertThrows(IllegalArgumentException::class.java) { library().list() }
        assertArrayEquals(original, file().readBytes())
    }

    @Test fun explicitDeleteKeepsOtherNotesAcrossRestart() {
        val removed = library().addUserNote("À supprimer", "Première note")
        library().addUserNote("À conserver", "Seconde note")
        assertTrue(library().remove(removed.id))
        assertEquals("Seconde note", library().list().single().excerpt)
    }

    @Test fun expiredNotesAreExcludedFromBothSearchAndConversationalContext() {
        file().writeText(legacy(entry("expired", "note expirée", 100L), entry("valid", "note valide")))
        assertEquals("valid", library().search("note", 101L).single().id)
        assertEquals("valid", library().contextFor("inconnu", 101L).single().id)
    }

    @Test fun companionNamespacesRemainSeparateAndCopiedCiphertextFailsAuthentication() {
        library("alpha").addUserNote("Alpha", "Note privée alpha")
        assertTrue(library("beta").list().isEmpty())
        file("beta").writeBytes(file("alpha").readBytes())
        assertThrows(Exception::class.java) { library("beta").list() }
        assertEquals("Note privée alpha", library("alpha").list().single().excerpt)
    }

    @Test fun fullLibraryReturnsTheSpecificCapacityFailureWithoutLosingNotes() {
        val entries = (0 until 500).map { entry("id-$it", "note $it") }
        file().writeText(legacy(*entries.toTypedArray()))
        assertEquals(500, library().list().size)
        val before = file().readBytes()
        assertThrows(KnowledgeCapacityReached::class.java) { library().addUserNote("Titre", "note supplémentaire") }
        assertArrayEquals(before, file().readBytes())
    }
}
