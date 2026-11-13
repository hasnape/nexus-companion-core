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
import java.io.ByteArrayOutputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.zip.GZIPOutputStream
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class LocalKnowledgeLibraryTest {
    @Test fun aLargeEncryptedLibraryRemainsSearchableAfterRestartAndDeletion() {
        val id = "twenty-thousand"
        val entries = (0 until 20000).map { entry("id-$it", "Dossier $it").put("excerpt", "Repère confidentiel repere$it.") }
        val bytes = legacy(*entries.toTypedArray()).toByteArray()
        val encrypted = JvmCipher(id).encrypt(KnowledgePayload.encode(bytes))
        file(id).writeBytes(encrypted)
        val store = library(id)
        assertEquals(20000,store.list().size)
        assertEquals("id-19999",store.search("repere19999").single().id)
        assertTrue(store.remove("id-19999"))
        assertTrue(store.search("repere19999").isEmpty())
        assertEquals(19999,library(id).list().size)
        assertEquals("id-12345",library(id).search("repere12345").single().id)
    }
    @Test fun cachedRankingsObserveExpirationExternalWritesAndCiphertextTampering() {
        val id = "index-freshness"
        file(id).writeText(legacy(entry("expiring","robot bientôt expiré",100),entry("kept","robot permanent")))
        val store = library(id)
        assertEquals(2,store.search("robot",99).size)
        assertEquals(listOf("kept"),store.search("robot",100).map { it.id })
        library(id).addUserNote("Robot récent","robot nouveau")
        assertEquals(2,store.search("robot",101).size)
        val corrupted=file(id).readBytes().also { it[it.lastIndex]=(it.last().toInt() xor 1).toByte() }
        file(id).writeBytes(corrupted)
        assertThrows(Exception::class.java) { store.search("robot",101) }
        assertArrayEquals(corrupted,file(id).readBytes())
    }
    @Test fun growsPastTheOldCapacityAndPreservesLongNotesAcrossRestart() {
        val entries = (0 until 510).map { entry("id-$it", "note $it") }
        val id = "expanded"
        file(id).writeText(legacy(*entries.toTypedArray()))
        val original = "Observation locale. ".repeat(398) + "FIN EXACTE."
        val saved = library(id).addUserNote("Dossier long", original)
        assertEquals(511, library(id).list().size)
        assertEquals(original, library(id).list().last().excerpt)
        assertEquals(saved.entry.id, library(id).list().last().id)
        assertFalse(library(id).addUserNote("Dossier long", original).created)
    }
    @Test fun enlargedConversationalSelectionAndSearchKeepOlderMatchingEvidence() {
        val store = library("context-growth")
        repeat(110) { store.addUserNote("Robot $it", "Le robot contient le module numéro $it.") }
        assertEquals(110, store.contextFor("robot").size)
        assertEquals(100, store.search("robot").size)
        assertEquals(110, store.list().size)
    }
    @Test fun unchangedEncryptedFilesReuseTheParsedSnapshotButExternalChangesAreObserved() {
        val id = "cached"
        library(id).addUserNote("Repère", "Une information locale.")
        var decryptions = 0
        val delegate = JvmCipher(id)
        val cipher = object : KnowledgeCipher {
            override fun isEncrypted(bytes: ByteArray) = delegate.isEncrypted(bytes)
            override fun encrypt(plaintext: ByteArray) = delegate.encrypt(plaintext)
            override fun decrypt(blob: ByteArray): ByteArray { decryptions++; return delegate.decrypt(blob) }
        }
        val cached = LocalKnowledgeLibrary(context, id, cipher)
        repeat(10) { assertEquals(1, cached.list().size) }
        assertEquals(1, decryptions)
        library(id).addUserNote("Autre", "Une autre information.")
        assertEquals(2, cached.list().size); assertEquals(2, decryptions)
        val corrupted = file(id).readBytes().also { it[it.lastIndex] = (it.last().toInt() xor 1).toByte() }
        file(id).writeBytes(corrupted)
        assertThrows(Exception::class.java) { cached.list() }
        assertArrayEquals(corrupted, file(id).readBytes())
    }

    @Test fun cosmeticDuplicatesKeepTheOriginalIdentityAndSpelling() {
        val store = library("cosmetic")
        val original = store.addUserNote("Thé vert", "Je préfère le thé vert.")
        val duplicate = store.addUserNote("THÉ VERT", "Je  préfère le thé vert!")
        assertFalse(duplicate.created)
        assertEquals(original.entry.id, duplicate.entry.id)
        assertEquals(original.entry.recordedAt, duplicate.entry.recordedAt)
        assertEquals("Je préfère le thé vert.", store.list().single().excerpt)
    }

    @Test fun adaptiveCompressionIsLosslessNoLargerThanTheOldCodecAndMeasurable() {
        val source = legacy(*(0 until 80).map { entry("id-$it", "Repère $it").put("excerpt", "Information locale sur le compagnon et ses projets. ".repeat(24) + it) }.toTypedArray()).toByteArray()
        val old = ByteArrayOutputStream().also { target -> GZIPOutputStream(target).use { it.write(source) } }.toByteArray()
        val packed = KnowledgePayload.encode(source)
        assertTrue(packed.size <= old.size)
        assertArrayEquals(source, KnowledgePayload.decode(packed))
        val store = library("stats")
        repeat(12) { store.addUserNote("Repère $it", "Information locale sur le compagnon. ".repeat(30) + it) }
        val stats = store.statistics()
        assertEquals(12, stats.notes)
        assertEquals(file("stats").length().toInt(), stats.storedBytes)
        assertTrue(stats.compressionPercent > 70)
        assertTrue(stats.payloadBytes < stats.jsonBytes)
        java.io.File("build/reports/memory-compression.json").also { it.parentFile?.mkdirs() }.writeText(
            JSONObject().put("fixtureNotes", stats.notes).put("jsonBytes", stats.jsonBytes).put("encryptedBytes", stats.storedBytes)
                .put("compressionPercent", stats.compressionPercent).put("oldGzipBytes", old.size).put("adaptiveGzipBytes", packed.size).toString())
    }
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

    @Test fun opensOlderEncryptedJsonAndCompressesTheNextWrite() {
        val entries = (0 until 24).map {
            entry("id-$it", "Compte rendu $it")
                .put("excerpt", "Échange local sur la sécurité et la mémoire. ".repeat(36) + "Échange $it.")
        }
        val cipher = JvmCipher("test")
        val oldFile = cipher.encrypt(legacy(*entries.toTypedArray()).toByteArray(Charsets.UTF_8))
        file().writeBytes(oldFile)
        assertEquals(24, library().list().size)
        assertArrayEquals(oldFile, file().readBytes())

        assertTrue(library().addUserNote("Nouvelle note", "Une information à retenir").created)
        val compressedFile = file().readBytes()
        assertTrue(cipher.isEncrypted(compressedFile))
        assertTrue("Repeated synthetic text should compress", compressedFile.size < oldFile.size / 4)
        assertEquals(25, library().list().size)
        assertEquals(entries.first().getString("excerpt"), library().list().first().excerpt)
    }

    @Test fun rejectsExcessiveDecompressionBeforeAnyWrite() {
        val output = ByteArrayOutputStream()
        val block = ByteArray(8192)
        GZIPOutputStream(output).use { stream ->
            repeat(KnowledgePayload.MAX_JSON_BYTES / block.size + 1) { stream.write(block) }
        }
        val stored = JvmCipher("test").encrypt(output.toByteArray())
        file().writeBytes(stored)
        assertThrows(IllegalArgumentException::class.java) { library().list() }
        assertThrows(IllegalArgumentException::class.java) { library().addUserNote("Note", "Texte") }
        assertArrayEquals(stored, file().readBytes())
    }

    @Test fun concurrentInstancesKeepOneCopyOfEachExactNote() {
        val instances = List(6) { library() }
        val start = CountDownLatch(1)
        val pool = Executors.newFixedThreadPool(instances.size)
        try {
            val writes = instances.mapIndexed { index, library ->
                pool.submit<LocalKnowledgeLibrary.NoteSave> {
                    start.await()
                    val note = index % 3
                    library.addUserNote("Titre $note", "Information $note")
                }
            }
            start.countDown()
            val results = writes.map { it.get(10, TimeUnit.SECONDS) }
            assertEquals(3, results.count { it.created })
            assertEquals(3, results.map { it.entry.id }.distinct().size)
            assertEquals(3, library().list().size)
        } finally {
            pool.shutdownNow()
        }
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
        assertTrue(library().remove(removed.entry.id))
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
        val entries = (0 until NativeCapacity.MAX_LIBRARY_ENTRIES).map { entry("id-$it", "note $it") }
        file().writeBytes(JvmCipher("test").encrypt(KnowledgePayload.encode(legacy(*entries.toTypedArray()).toByteArray())))
        assertEquals(NativeCapacity.MAX_LIBRARY_ENTRIES, library().list().size)
        val before = file().readBytes()
        assertFalse(library().addUserNote("note 0", "note 0").created)
        assertThrows(KnowledgeCapacityReached::class.java) { library().addUserNote("Titre", "note supplémentaire") }
        assertArrayEquals(before, file().readBytes())
    }
}
