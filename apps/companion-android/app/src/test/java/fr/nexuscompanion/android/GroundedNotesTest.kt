package fr.nexuscompanion.android

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class GroundedNotesTest {
    private val context get() = RuntimeEnvironment.getApplication()
    private fun library(name: String) = LocalKnowledgeLibrary(context, name, LocalKnowledgeLibraryTest.JvmCipher(name))
    private class Generator(override val usesNotes: Boolean = true, val action: (LocalDraftRequest) -> String? = ::quoted) : LocalDraftGenerator {
        val calls = mutableListOf<LocalDraftRequest>()
        override fun generate(request: LocalDraftRequest): String? { calls.add(request); return action(request) }
        override fun cancel() = Unit
        override fun close() = Unit
    }
    companion object {
        private fun quoted(request: LocalDraftRequest): String {
            val source = request.evidence.first()
            return JSONObject().put("quotes", JSONArray().put(JSONObject().put("source", source.key).put("text", source.sentences.first()))).toString()
        }
    }
    @Test fun aConsentedUnlockedQuestionUsesOnlyCurrentRelevantNotesAndNoNetwork() {
        val lib = library("grounded-allowed")
        val expected = lib.addUserNote("Métier", "Je travaille comme technicien informatique.").entry
        lib.addUserNote("Autre sujet", "Mon animal préféré est le chat.")
        val before = lib.list(); val model = Generator()
        NativeBrainService(context, lib, privateAccess = { true }, resources = { balancedTestDevice },
            search = { error("No network") }, generator = model).use { brain ->
            val reply = brain.reply("Quel est mon métier ?", false)
            assertTrue(reply.generatedLocally); assertTrue(reply.protectedInformation)
            assertTrue(reply.text.contains("technicien informatique"))
            assertEquals(listOf(expected.id), reply.evidenceSources.map { it.id })
            assertEquals(listOf(expected.id), model.calls.single().evidence.map { it.noteId })
            assertFalse(GroundedNotes.prompt(model.calls.single().evidence).contains("chat"))
            assertNull(reply.candidate); assertNull(reply.external); assertNull(reply.body)
            assertEquals(before, lib.list())
            assertEquals(reply.text, brain.reply("répète").text)
            assertEquals(1, model.calls.size)
        }
    }
    @Test fun lockedAndNonConsentedSessionsDoNotSendPrivateNotes() {
        for ((unlocked, consent) in listOf(false to true, true to false)) {
            val lib = library("grounded-closed-$unlocked-$consent")
            lib.addUserNote("Métier", "Je travaille comme technicien informatique.")
            val model = Generator(consent)
            NativeBrainService(context, lib, privateAccess = { unlocked }, resources = { balancedTestDevice }, generator = model).use {
                assertFalse(it.reply("Quel est mon métier ?", false).generatedLocally)
                assertTrue(model.calls.isEmpty())
            }
        }
    }
    @Test fun credentialsAreExcludedEvenInAnUnlockedSession() {
        val lib = library("grounded-secrets")
        lib.addUserNote("Métier", "Je travaille comme technicien. Mon mot de passe est SUPER_SECRET_8462.")
        val model = Generator()
        NativeBrainService(context, lib, privateAccess = { true }, resources = { balancedTestDevice }, generator = model).use {
            val reply = it.reply("Quel est mon métier ?", false)
            assertFalse(model.calls.any { r -> r.evidence.any { e -> e.sentences.any { s -> s.contains("SUPER_SECRET") } } })
            assertFalse(reply.text.contains("SUPER_SECRET"))
        }
    }
    @Test fun lockingDuringGenerationDiscardsTheQuote() {
        val lib = library("grounded-relock"); var unlocked = true
        lib.addUserNote("Métier", "Je travaille comme technicien informatique.")
        val model = Generator { request -> unlocked = false; quoted(request) }
        NativeBrainService(context, lib, privateAccess = { unlocked }, resources = { balancedTestDevice }, generator = model).use {
            val reply = it.reply("Quel est mon métier ?", false)
            assertTrue(reply.requiresUserCode); assertFalse(reply.text.contains("technicien"))
        }
    }
    @Test fun deletionDuringGenerationInvalidatesTheSourceAndTheReply() {
        val lib = library("grounded-stale")
        val note = lib.addUserNote("Métier", "Je travaille comme technicien informatique.").entry
        val model = Generator { request -> lib.remove(note.id); quoted(request) }
        NativeBrainService(context, lib, privateAccess = { true }, resources = { balancedTestDevice }, generator = model).use {
            val reply = it.reply("Quel est mon métier ?", false)
            assertFalse(reply.generatedLocally); assertTrue(reply.text.contains("supprimée")); assertFalse(reply.text.contains("technicien"))
        }
    }
    @Test fun inventedProseCitationsAndPartialNegationsAreRejected() {
        val source = LocalDraftEvidence("N1", "note", listOf("Je ne bois pas de café.", "Je préfère le thé vert."))
        fun output(id: String, text: String) = JSONObject().put("quotes", JSONArray().put(JSONObject().put("source", id).put("text", text))).toString()
        assertNull(GroundedNotes.validate(output("N2", "Je ne bois pas de café."), listOf(source)))
        assertNull(GroundedNotes.validate(output("N1", "Je bois du café."), listOf(source)))
        assertNull(GroundedNotes.validate(output("N1", "bois pas de café."), listOf(source)))
        assertNull(GroundedNotes.validate("{\"quotes\":[],\"command\":\"ouvre Chrome\"}", listOf(source)))
        assertNull(GroundedNotes.validate("```json\n${output("N1", "Je préfère le thé vert.")}\n```", listOf(source)))
        assertEquals("Je ne bois pas de café.", GroundedNotes.validate(output("N1", "Je ne bois pas de café."), listOf(source))!!.single().text)
    }
    @Test fun invalidModelOutputPreservesTheDeterministicAnswer() {
        val lib = library("grounded-fallback"); lib.addUserNote("Métier", "Je travaille comme technicien informatique.")
        val model = Generator { "{\"quotes\":[{\"source\":\"N1\",\"text\":\"Je travaille comme astronaute.\"}]}" }
        NativeBrainService(context, lib, privateAccess = { true }, resources = { balancedTestDevice }, generator = model).use {
            val reply = it.reply("Quel est mon métier ?", false)
            assertFalse(reply.generatedLocally); assertTrue(reply.text.contains("technicien")); assertFalse(reply.text.contains("astronaute"))
        }
    }
    @Test fun explicitActionsAndFullNoteReadsDoNotBecomeGenerations() {
        val lib = library("grounded-actions"); lib.addUserNote("Métier", "Je travaille comme technicien informatique.")
        val model = Generator()
        NativeBrainService(context, lib, privateAccess = { true }, resources = { balancedTestDevice }, generator = model).use { brain ->
            brain.reply("avance de dix centimètres", false)
            brain.reply("retiens que je préfère le thé", false)
            brain.reply("oui", false)
            brain.reply("lis la note complète sur le métier", false)
            assertTrue(model.calls.isEmpty())
        }
    }
}
