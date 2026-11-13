package fr.nexuscompanion.android

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class LocalGenerationTest {
    private class FakeGenerator(var answer: String? = "Un volcan est une ouverture de la croûte terrestre.") : LocalDraftGenerator {
        val requests = mutableListOf<LocalDraftRequest>()
        var cancelled = false
        override fun generate(request: LocalDraftRequest) = answer.also { requests.add(request) }
        override fun cancel() { cancelled = true }
        override fun close() = cancel()
    }
    private fun library(name: String) = LocalKnowledgeLibrary(RuntimeEnvironment.getApplication(), name, LocalKnowledgeLibraryTest.JvmCipher(name))
    @Test fun publicFallbackUsesLocalModelOfflineWithoutLearningOrNetwork() {
        val context = RuntimeEnvironment.getApplication(); val notes = library("draft-public"); val generator = FakeGenerator()
        NativeBrainService(context, notes, privateAccess = { false }, resources = { balancedTestDevice }, search = { error("Network must not be used") }, generator = generator).use { brain ->
            val answer = brain.reply("Qu’est-ce qu’un volcan ?", onlineEnabled = false)
            assertTrue(answer.generatedLocally); assertTrue(answer.text.contains("croûte terrestre"))
            assertEquals(1, generator.requests.size); assertTrue(notes.list().isEmpty()); assertNull(answer.candidate)
            brain.reply("oui", onlineEnabled = false)
            assertTrue(notes.list().isEmpty())
            brain.cancelGeneration(); assertTrue(generator.cancelled)
        }
    }
    @Test fun personalMemoryExplicitActionsAndSavedEvidenceNeverGoToTheGenerator() {
        val context = RuntimeEnvironment.getApplication(); val notes = library("draft-gates"); val generator = FakeGenerator()
        notes.addApprovedSource("Volcan", "Un volcan libère de la lave à la surface de la Terre.", "external:fixture-volcan")
        notes.addUserNote("Nom", "Je m’appelle Amine.")
        NativeBrainService(context, notes, privateAccess = { false }, resources = { balancedTestDevice }, search = { null }, generator = generator).use { brain ->
            assertFalse(brain.reply("Qu’est-ce qu’un volcan ?", false).generatedLocally)
            brain.clearConversation(); brain.reply("Quel est mon prénom ?", false)
            brain.clearConversation(); brain.reply("Quel est mon code utilisateur ?", false)
            brain.clearConversation(); brain.reply("retiens que je préfère le thé", false)
            brain.clearConversation(); brain.reply("avance de dix centimètres", false)
            brain.clearConversation(); brain.reply("ouvre Chrome", false)
            assertTrue(generator.requests.isEmpty())
        }
    }
    @Test fun unavailableModelKeepsTheExistingFallbackAndConfirmation() {
        val context = RuntimeEnvironment.getApplication(); val generator = FakeGenerator(null)
        NativeBrainService(context, library("draft-fallback"), privateAccess = { false }, resources = { balancedTestDevice }, search = { null }, generator = generator).use { brain ->
            val result = brain.reply("Qu’est-ce qu’un volcan ?")
            assertFalse(result.generatedLocally); assertTrue(result.text.contains("Wikipédia"))
            assertEquals(1, generator.requests.size)
        }
    }
    @Test fun outputSanitizerRejectsInternalTagsAndIncompleteShortAnswers() {
        assertNull(LocalDraftPolicy.clean("<think>raisonnement</think> Une réponse."))
        assertNull(LocalDraftPolicy.clean("<|im_start|>assistant"))
        assertNull(LocalDraftPolicy.clean("Une réponse incomplète"))
        assertEquals("Une explication complète.", LocalDraftPolicy.clean("Une explication complète. Et une suite coupée"))
        assertEquals("Paris est la capitale de la France.", LocalDraftPolicy.clean("Paris est la capitale de la France."))
    }
    @Test fun resourcePressureAndLowBatteryDisableGeneration() {
        val ready = balancedTestDevice.copy(availableRam = 2L shl 30, abis = listOf("arm64-v8a"))
        assertTrue(LocalDraftPolicy.permitted(ready))
        assertFalse(LocalDraftPolicy.permitted(ready.copy(availableRam = 512L shl 20)))
        assertFalse(LocalDraftPolicy.permitted(ready.copy(thermalStatus = 2)))
        assertFalse(LocalDraftPolicy.permitted(ready.copy(batteryPercent = 9, charging = false)))
        assertFalse(LocalDraftPolicy.permitted(ready.copy(powerSave = true)))
        assertFalse(LocalDraftPolicy.permitted(ready.copy(abis = listOf("armeabi-v7a"))))
    }
    @Test fun badOrCancelledImportPreservesThePreviousFile() {
        val context = RuntimeEnvironment.getApplication(); val store = LocalModelStore(context, availableBytes = { 2L shl 30 })
        store.file.writeText("previous")
        assertThrows(IllegalArgumentException::class.java) { store.import(ByteArrayInputStream(byteArrayOf(1, 2, 3))) }
        assertEquals("previous", store.file.readText()); assertFalse(store.installed())
        assertThrows(IllegalStateException::class.java) { store.import(ByteArrayInputStream(byteArrayOf(4))) { true } }
        assertEquals("previous", store.file.readText())
    }
    @Test fun voiceReportRequiresRealCompletedStagesAndNeverPromotesInterruption() {
        val report = VoiceDiagnosticReport()
        assertFalse(JSONObject(report.json()).getBoolean("completed"))
        report.first = "bonjour Nexus"; report.second = "le microphone fonctionne"
        report.playbackStarted = true; report.playbackCompleted = true; report.resumed = true
        assertTrue(JSONObject(report.json()).getBoolean("completed"))
        report.microphoneDuringPlayback = true
        assertFalse(JSONObject(report.json()).getBoolean("completed"))
        report.microphoneDuringPlayback = false; report.interrupted = true
        assertFalse(JSONObject(report.json()).getBoolean("completed"))
    }
    @Test fun weakestWordCanBlockAnOtherwiseConfidentConfirmation() {
        val hypothesis = SpeechHypothesis("oui", 0.8f, null, 0.15f)
        assertTrue(VoiceTranscriptPolicy.uncertainConfirmation(hypothesis))
        assertNull(VoiceTranscriptPolicy.select(listOf(hypothesis), true))
        assertEquals("stop", VoiceTranscriptPolicy.select(listOf(SpeechHypothesis("stop", 0.1f)), true))
    }
    @Test fun contextualRecallUsesCurrentSourcesAndRejectsDeletedEvidence() {
        val context = RuntimeEnvironment.getApplication(); val notes = library("recovered-follow-up")
        val note = notes.addUserNote("Sauvegarde", "Une sauvegarde est une copie des données. Elle permet de retrouver les fichiers après une panne.").entry
        authorizedBrain(context, notes).use { brain ->
            assertTrue(brain.reply("Qu’est-ce qu’une sauvegarde ?").evidenceSources.any { it.id == note.id })
            assertTrue(brain.reply("Lis la note complète").text.contains("panne"))
            notes.remove(note.id)
            assertTrue(brain.reply("Pourquoi ?").text.contains("supprimée"))
        }
    }
}
