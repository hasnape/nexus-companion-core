package fr.nexuscompanion.android

import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class NativeBrainServiceTest {
    @Test fun sharedBrainUsesEncryptedNotesButNeverPersistsAProposalByItself() {
        val context = RuntimeEnvironment.getApplication()
        val library = LocalKnowledgeLibrary(context, "bridge", LocalKnowledgeLibraryTest.JvmCipher("bridge"))
        library.addUserNote("Préférence", "Je préfère le thé vert")
        val file = File(context.filesDir, "knowledge-bridge.json")
        val before = file.readBytes()
        NativeBrainService(context, library).use { brain ->
            val recall = brain.reply("de quoi te souviens-tu ?")
            assertTrue(recall.text.contains("thé vert"))
            assertTrue(recall.notesAvailable)
            val proposal = brain.reply("retiens que je préfère les réponses courtes")
            assertNotNull(proposal.candidate)
            assertArrayEquals(before, file.readBytes())
        }
    }

    @Test fun escapedLargeContextIsBoundedWithoutMutatingAnyStoredNote() {
        val context = RuntimeEnvironment.getApplication()
        val library = LocalKnowledgeLibrary(context, "large", LocalKnowledgeLibraryTest.JvmCipher("large"))
        repeat(8) { library.addUserNote("note-$it", "\u0001".repeat(1990) + "note-$it") }
        val file = File(context.filesDir, "knowledge-large.json")
        val before = file.readBytes()
        NativeBrainService(context, library).use { brain ->
            val recall = brain.reply("mes notes")
            assertTrue(recall.notesAvailable)
            assertTrue(recall.text.length < 1200)
            assertTrue(recall.text.contains("notes complètes"))
            assertArrayEquals(before, file.readBytes())
        }
    }
}
