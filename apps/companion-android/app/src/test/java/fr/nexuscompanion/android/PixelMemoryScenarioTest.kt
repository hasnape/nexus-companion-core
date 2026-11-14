package fr.nexuscompanion.android

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Runs the same note/ask flow without writing to a real companion namespace.
 * A fake model selects exact sentences, so this checks the Android routing and
 * privacy gates, not the quality or speed of the installed GGUF model.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PixelMemoryScenarioTest {
    private class QuotingGenerator : LocalDraftGenerator {
        override val usesNotes = true
        val requests = mutableListOf<LocalDraftRequest>()
        override fun generate(request: LocalDraftRequest): String? {
            requests.add(request)
            val source = request.evidence.firstOrNull() ?: return null
            val quotes = JSONArray()
            for (sentence in source.sentences.take(2)) {
                quotes.put(JSONObject().put("source", source.key).put("text", sentence))
            }
            return JSONObject().put("quotes", quotes).toString()
        }
        override fun cancel() = Unit
        override fun close() = Unit
    }

    @Test fun savedImaginaryAnimalNoteCanBeAskedOfflineWithoutChangingOtherMemories() {
        val context = RuntimeEnvironment.getApplication()
        // An isolated, encrypted test namespace; never the owner's actual memory.
        val notes = LocalKnowledgeLibrary(context, "fixture-pixel-v1",
            LocalKnowledgeLibraryTest.JvmCipher("fixture-pixel-v1"))
        val note = notes.addUserNote("Animal imaginaire",
            "Mon animal imaginaire s'appelle Pixel. C'est un petit renard bleu qui adore les fraises.").entry
        val before = notes.list()
        val model = QuotingGenerator()
        NativeBrainService(context, notes, privateAccess = { true },
            resources = { balancedTestDevice },
            search = { error("Aucune recherche Internet autorisée") },
            generator = model).use { nexus ->
            val response = nexus.reply(
                "Comment s'appelle mon animal imaginaire et qu'aime-t-il manger ?",
                onlineEnabled = false)
            assertTrue("Nexus doit citer sa note locale", response.text.contains("Pixel"))
            assertTrue("Nexus doit citer les fraises", response.text.contains("fraises"))
            assertTrue("Le test doit appeler le chemin LLM simulé", response.generatedLocally)
            assertTrue(model.requests.any { call -> call.evidence.any { it.noteId == note.id } })
            assertEquals("Les notes ne doivent pas être modifiées par une réponse", before, notes.list())
        }
    }

    @Test fun missingBirthdayMustNotBecomeAFabricatedMemory() {
        val context = RuntimeEnvironment.getApplication()
        val notes = LocalKnowledgeLibrary(context, "fixture-pixel-unknown",
            LocalKnowledgeLibraryTest.JvmCipher("fixture-pixel-unknown"))
        notes.addUserNote("Animal imaginaire",
            "Mon animal imaginaire s'appelle Pixel. C'est un petit renard bleu qui adore les fraises.")
        val model = object : LocalDraftGenerator {
            override val usesNotes = true
            override fun generate(request: LocalDraftRequest): String =
                """{"quotes":[{"source":"N1","text":"Pixel est né le 14 février."}]}"""
            override fun cancel() = Unit
            override fun close() = Unit
        }
        NativeBrainService(context, notes, privateAccess = { true },
            resources = { balancedTestDevice }, search = { error("No network") },
            generator = model).use { nexus ->
            val reply = nexus.reply("Quelle est la date de naissance de Pixel ?", onlineEnabled = false)
            assertFalse(reply.text.contains("14 février"))
            assertEquals(1, notes.list().size)
            assertTrue(notes.list().single().excerpt.contains("fraises"))
        }
    }
}
