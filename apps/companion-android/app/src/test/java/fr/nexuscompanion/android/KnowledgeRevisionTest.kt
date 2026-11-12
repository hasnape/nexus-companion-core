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
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class KnowledgeRevisionTest {
    private val context get() = RuntimeEnvironment.getApplication()
    private fun store(id: String = "revisions") = LocalKnowledgeLibrary(context,id,LocalKnowledgeLibraryTest.JvmCipher(id))
    private fun file(id: String = "revisions") = File(context.filesDir,"knowledge-$id.json")

    @Test fun updatesPreserveTheOriginalTextSourceAndExpiryInEncryptedHistory() {
        val library = store()
        val original = library.add("Métier","Je travaille comme technicien. Je préfère le thé.","https://fr.wikipedia.org/?curid=42",expiresAt=Long.MAX_VALUE)
        assertEquals(1,library.search("technicien").size)
        val revised = library.replace(original,"Métier actuel","Je travaille comme ingénieur. Je préfère le thé.")
        assertTrue(revised.created)
        assertNotEquals(original.id,revised.entry.id)
        assertTrue(revised.entry.manuallyEdited)
        assertFalse(revised.entry.verified)
        assertEquals(original.source,revised.entry.source)
        assertEquals(original.expiresAt,revised.entry.expiresAt)
        assertNull(library.get(original.id))
        assertEquals(listOf(revised.entry),store().list())
        assertTrue(library.search("technicien").isEmpty())
        assertEquals(revised.entry.id,library.search("ingenieur").single().id)
        val archived = store().history(revised.entry.id).single()
        assertEquals(original.copy(supersededBy=revised.entry.id),archived)
        assertEquals(1,library.statistics().notes)
        assertEquals(1,library.statistics().archivedNotes)
        assertFalse(file().readText().contains("technicien"))
    }

    @Test fun restorationCreatesAnotherVersionAndNeverErasesAnIntermediateCorrection() {
        val library=store()
        val first=library.addUserNote("Profil","Première information.").entry
        val second=library.replace(first,"Profil","Deuxième information.").entry
        val third=library.replace(second,"Profil","Troisième information.").entry
        assertEquals(listOf(second.id,first.id),library.history(third.id).map { it.id })
        val restored=library.replace(third,first.title,first.excerpt).entry
        assertEquals(first.excerpt,store().list().single().excerpt)
        assertEquals(listOf(third.id,second.id,first.id),store().history(restored.id).map { it.id })
    }

    @Test fun unchangedInvalidAndStaleEditsNeverOverwriteTheCurrentVersion() {
        val library=store();val original=library.addUserNote("Note","Texte original.").entry
        val before=file().readBytes()
        assertFalse(library.replace(original,original.title,original.excerpt).created)
        assertArrayEquals(before,file().readBytes())
        assertThrows(InvalidKnowledgeNote::class.java) { library.replace(original,"Note","") }
        assertArrayEquals(before,file().readBytes())
        val current=store().replace(original,"Note","Modification concurrente.").entry
        val concurrent=file().readBytes()
        assertThrows(KnowledgeChanged::class.java) { library.replace(original,"Note","Ancien formulaire.") }
        assertThrows(KnowledgeChanged::class.java) { library.remove(original.id,original) }
        assertArrayEquals(concurrent,file().readBytes())
        assertEquals(current,library.list().single())
    }

    @Test fun explicitDeletionRemovesTheWholeChainAndKeepsOtherNotes() {
        val library=store();val original=library.addUserNote("Note","Ancien secret local.").entry
        val current=library.replace(original,"Note","Nouveau texte.").entry
        val other=library.addUserNote("Autre","À conserver.").entry
        assertTrue(library.remove(current.id,current))
        assertEquals(listOf(other),store().list())
        assertEquals(0,store().statistics().archivedNotes)
        assertThrows(KnowledgeChanged::class.java) { library.history(current.id) }
    }

    @Test fun failedEncryptionCannotLeaveAnArchivedOriginalOrLoseItsText() {
        val original=store().addUserNote("Note","Texte intact.").entry
        val before=file().readBytes();val delegate=LocalKnowledgeLibraryTest.JvmCipher("revisions")
        val broken=object:KnowledgeCipher {
            override fun isEncrypted(bytes:ByteArray)=delegate.isEncrypted(bytes)
            override fun decrypt(blob:ByteArray)=delegate.decrypt(blob)
            override fun encrypt(plaintext:ByteArray):ByteArray=throw IOException("Disk/cipher unavailable")
        }
        val library=LocalKnowledgeLibrary(context,"revisions",broken)
        assertThrows(IOException::class.java) { library.replace(original,"Note","Une correction.") }
        assertArrayEquals(before,file().readBytes())
        assertEquals(original,store().list().single())
        assertTrue(store().history(original.id).isEmpty())
    }

    @Test fun rejectsCyclicDanglingOrAmbiguousVersionMetadataBeforeMigratingPlaintext() {
        fun entry(id:String,next:String?)=JSONObject().put("id",id).put("title","Note").put("excerpt","Texte")
            .put("source","user:manual").put("recordedAt",1L).put("expiresAt",JSONObject.NULL).put("verified",false)
            .apply { if(next!=null) put("supersededBy",next) }
        val cases=listOf(
            listOf(entry("a","a")),listOf(entry("a","missing")),
            listOf(entry("a","b"),entry("b","a")),
            listOf(entry("a","c"),entry("b","c"),entry("c",null)),
            listOf(entry("a",null),entry("a",null)),
            listOf(entry("a",null).put("manuallyEdited","true"))
        )
        cases.forEachIndexed { i,entries ->
            val id="invalid-history-$i"
            val bytes=JSONObject().put("schema",2).put("entries",JSONArray(entries)).toString().toByteArray()
            file(id).writeBytes(bytes)
            assertThrows(IllegalArgumentException::class.java) { store(id).list() }
            assertArrayEquals(bytes,file(id).readBytes())
        }
    }
    @Test fun aFullVersionArchiveFailsWithoutLosingTheCurrentTextAndUsesIterativeTraversal() {
        val id="full-archive"
        val count=NativeCapacity.MAX_LIBRARY_RECORDS
        val entries=(0 until count).map { i ->
            JSONObject().put("id","v$i").put("title","Note").put("excerpt","Version $i.")
                .put("source","user:manual").put("recordedAt",1L).put("expiresAt",JSONObject.NULL).put("verified",false)
                .apply { if(i<count-1) put("supersededBy","v${i+1}") }
        }
        val bytes=JSONObject().put("schema",2).put("entries",JSONArray(entries)).toString().toByteArray()
        file(id).writeBytes(LocalKnowledgeLibraryTest.JvmCipher(id).encrypt(KnowledgePayload.encode(bytes)))
        val library=store(id);val current=library.list().single();val before=file(id).readBytes()
        assertEquals(count-1,library.history(current.id).size)
        assertThrows(KnowledgeCapacityReached::class.java) { library.replace(current,"Note","Version supplémentaire.") }
        assertArrayEquals(before,file(id).readBytes())
        assertTrue(library.remove(current.id,current))
        assertTrue(library.list().isEmpty())
        assertEquals(0,library.statistics().archivedNotes)
    }
}
