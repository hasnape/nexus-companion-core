package fr.nexuscompanion.android

import org.junit.Assert.*
import org.junit.Test

class VoiceSessionTest {
    private class FakePlatform : VoicePlatform {
        data class Task(val due: Long, val action: () -> Unit, var canceled: Boolean = false)
        inner class Recognition(val events: RecognitionEvents) : VoiceRecognition {
            var starts = 0
            var closed = false
            override fun start() { starts++; totalStarts++; if (autoReady) events.ready() }
            override fun close() {
                closed = true
                // Simulate a vendor recognizer returning an error synchronously during cancel.
                events.error(RecognitionFailure.TEMPORARY)
            }
        }
        inner class Speech(val events: SpeechEvents) : VoiceSpeech {
            var closed = false
            var stops = 0
            val utterances = mutableListOf<Pair<String, String>>()
            override fun speak(text: String, id: String): Boolean {
                utterances.add(text to id)
                return acceptsSpeech
            }
            override fun stop() { stops++ }
            override fun close() { closed = true }
        }
        var supported = true
        var acceptsSpeech = true
        var creationFails = false
        var autoReady = true
        var now = 0L
        var totalStarts = 0
        val recognizers = mutableListOf<Recognition>()
        val speakers = mutableListOf<Speech>()
        val tasks = mutableListOf<Task>()
        val recognition get() = recognizers.last()
        val speaker get() = speakers.last()
        override fun available() = supported
        override fun recognizer(events: RecognitionEvents): VoiceRecognition {
            if (creationFails) throw IllegalStateException("Service unavailable")
            return Recognition(events).also { recognizers.add(it) }
        }
        override fun speech(events: SpeechEvents): VoiceSpeech = Speech(events).also { speakers.add(it) }
        override fun after(delayMs: Long, action: () -> Unit): VoiceTask {
            val task = Task(now + delayMs, action)
            tasks.add(task)
            return VoiceTask { task.canceled = true }
        }
        fun advance(ms: Long) {
            val end = now + ms
            while (true) {
                val task = tasks.filter { !it.canceled && it.due <= end }.minByOrNull { it.due } ?: break
                tasks.remove(task)
                now = task.due
                task.action()
            }
            now = end
        }
    }
    private class Fixture {
        val platform = FakePlatform()
        val commands = mutableListOf<String>()
        val statuses = mutableListOf<String>()
        val mouths = mutableListOf<Boolean>()
        val sessions = mutableListOf<Boolean>()
        val transcripts = mutableListOf<Pair<String, Boolean>>()
        val session = VoiceSession(platform, statuses::add, commands::add, mouths::add, sessions::add,
            { text, final -> transcripts.add(text to final) })
        fun start() { session.start(); platform.speaker.events.ready(true) }
    }

    @Test fun silenceAndNoMatchDoNotExhaustTheFailureBudget() {
        val f = Fixture(); f.start()
        repeat(100) {
            f.platform.recognition.events.error(if (it % 2 == 0) RecognitionFailure.SILENCE else RecognitionFailure.NO_MATCH)
            f.platform.advance(500)
        }
        assertEquals(101, f.platform.totalStarts)
        assertEquals(listOf(true), f.sessions)
        assertTrue(f.commands.isEmpty())
    }

    @Test fun partialWordsAreVisibleButCannotWakeOrExecute() {
        val f = Fixture(); f.start()
        f.platform.recognition.events.partial("Nexus retiens une note")
        assertEquals("Nexus retiens une note" to false, f.transcripts.last())
        assertTrue(f.commands.isEmpty())
        assertTrue(f.platform.speaker.utterances.isEmpty())
        f.platform.recognition.events.result("Nexus retiens une note complète")
        assertEquals(listOf("retiens une note complète"), f.commands)
        assertEquals("Nexus retiens une note complète" to true, f.transcripts.last())
        f.platform.recognition.events.result("Nexus doublon")
        assertEquals(1, f.commands.size)
    }

    @Test fun commandDeadlineDoesNotExpireWhileTheUserIsSpeaking() {
        val f = Fixture(); f.start()
        f.platform.recognition.events.result("Nexus")
        f.platform.speaker.events.finished(f.platform.speaker.utterances.last().second)
        f.platform.advance(700)
        f.platform.recognition.events.speechStarted()
        f.platform.advance(20_000)
        f.platform.recognition.events.speechEnded()
        f.platform.advance(2_000)
        f.platform.recognition.events.result("Retiens ma phrase entière")
        assertEquals(listOf("Retiens ma phrase entière"), f.commands)
    }

    @Test fun partialsAlsoProtectCommandWindowWhenBeginningCallbackIsMissing() {
        val f = Fixture(); f.start(); f.session.listenForCommand()
        f.platform.recognition.events.partial("Retiens")
        f.platform.advance(20_000)
        f.platform.recognition.events.result("Retiens le texte final")
        assertEquals(listOf("Retiens le texte final"), f.commands)
    }

    @Test fun explicitDictationAcceptsOneCommandWithoutWakeOrSpokenAcknowledgement() {
        val f = Fixture(); f.start()
        val old = f.platform.recognition
        f.session.listenForCommand()
        assertTrue(old.closed)
        assertTrue(f.platform.speaker.utterances.isEmpty())
        old.events.result("Nexus ancien résultat")
        f.platform.recognition.events.result("Bonjour")
        assertEquals(listOf("Bonjour"), f.commands)
        f.platform.advance(500)
        f.platform.recognition.events.result("autre phrase ambiante")
        assertEquals(1, f.commands.size)
    }

    @Test fun directDictationWaitsForReadyBeforeStartingItsCommandWindow() {
        val f = Fixture(); f.platform.autoReady = false; f.start()
        f.session.listenForCommand()
        f.platform.advance(6_000)
        f.platform.recognition.events.ready()
        f.platform.advance(10_000)
        f.platform.recognition.events.result("bonjour")
        assertEquals(listOf("bonjour"), f.commands)
    }

    @Test fun directCommandWaitExpiresWithoutExtendingForSilence() {
        val f = Fixture(); f.start(); f.session.listenForCommand()
        repeat(26) {
            f.platform.recognition.events.error(RecognitionFailure.SILENCE)
            f.platform.advance(500)
        }
        f.platform.recognition.events.result("bonjour")
        assertTrue(f.commands.isEmpty())
    }

    @Test fun confidenceAlternativeRoutesThroughTheSameFinalOnlyPolicy() {
        val f = Fixture(); f.start()
        f.platform.recognition.events.alternatives(listOf(
            SpeechHypothesis("nexusbonjour", .84f), SpeechHypothesis("Nexus bonjour", .8f)
        ))
        assertEquals(listOf("bonjour"), f.commands)
        assertEquals("Nexus bonjour" to true, f.transcripts.last())
    }

    @Test fun missingReadyCallbackIsRetriedAndEventuallyStops() {
        val f = Fixture(); f.platform.autoReady = false; f.start()
        f.platform.advance(200_000)
        assertEquals(listOf(true, false), f.sessions)
        assertEquals(13, f.platform.totalStarts)
        assertTrue(f.platform.recognition.closed)
    }

    @Test fun missingFinalResultCannotLeaveTheMicrophoneStuck() {
        val f = Fixture(); f.start(); f.session.listenForCommand()
        val old = f.platform.recognition
        old.events.speechStarted(); old.events.speechEnded()
        f.platform.advance(7_000)
        old.events.partial("résultat tardif")
        f.platform.advance(1_500)
        assertTrue(old.closed)
        assertEquals(3, f.platform.totalStarts)
        old.events.result("Nexus périmé")
        f.platform.recognition.events.result("bonjour")
        assertEquals(listOf("bonjour"), f.commands)
    }

    @Test fun endlessPartialsHaveABoundedUtteranceDeadline() {
        val f = Fixture(); f.start()
        val old = f.platform.recognition
        repeat(6) { old.events.partial("Nexus en cours"); f.platform.advance(10_000) }
        f.platform.advance(500)
        assertTrue(old.closed)
        assertEquals(2, f.platform.totalStarts)
        assertTrue(f.commands.isEmpty())
    }

    @Test fun previewExpiresAndStopClearsItAndRejectsLatePartials() {
        val f = Fixture(); f.start()
        f.platform.recognition.events.partial("phrase privée")
        f.platform.advance(15_000)
        assertEquals("" to false, f.transcripts.last())
        f.platform.recognition.events.partial("nouvelle phrase")
        val old = f.platform.recognition
        f.session.stop()
        old.events.partial("ne doit pas apparaître")
        old.events.ready(); f.platform.advance(100_000)
        assertEquals("" to false, f.transcripts.last())
        assertEquals(1, f.platform.totalStarts)
    }

    @Test fun directDictationCannotReopenTheMicrophoneDuringWorkOrAfterStop() {
        val f = Fixture(); f.start()
        f.session.beginResponse(); f.session.listenForCommand()
        assertEquals(1, f.platform.totalStarts)
        f.session.stop(); f.session.listenForCommand(); f.platform.advance(100_000)
        assertEquals(1, f.platform.totalStarts)
    }

    @Test fun ambientSpeechDoesNotTriggerCommandsOrStopListening() {
        val f = Fixture(); f.start()
        repeat(40) { f.platform.recognition.events.result("conversation ambiante"); f.platform.advance(500) }
        assertEquals(41, f.platform.totalStarts)
        assertTrue(f.commands.isEmpty())
        assertEquals(listOf(true), f.sessions)
    }

    @Test fun wakeAndCommandInOneUtteranceAcceptPunctuation() {
        val f = Fixture(); f.start()
        f.platform.recognition.events.result("Nexus, bonjour !")
        assertEquals(listOf("bonjour !"), f.commands)
    }

    @Test fun similarlyPrefixedWordsDoNotWakeTheCompanion() {
        val f = Fixture(); f.start()
        f.platform.recognition.events.result("nexusbonjour")
        assertTrue(f.commands.isEmpty())
        assertTrue(f.platform.speaker.utterances.isEmpty())
    }

    @Test fun commandWindowStartsAfterTheAcknowledgementAndRemovesRepeatedWake() {
        val f = Fixture(); f.start()
        f.platform.recognition.events.result("Nexus")
        val id = f.platform.speaker.utterances.last().second
        f.platform.advance(13_000)
        assertEquals(1, f.platform.totalStarts)
        f.platform.speaker.events.finished(id)
        f.platform.advance(700)
        f.platform.recognition.events.result("Nexus : comment ça va ?")
        assertEquals(listOf("comment ça va ?"), f.commands)
    }

    @Test fun commandWithoutAnotherWakeIsAcceptedAfterAcknowledgement() {
        val f = Fixture(); f.start()
        f.platform.recognition.events.result("Nexus !")
        f.platform.speaker.events.finished(f.platform.speaker.utterances.last().second)
        f.platform.advance(700)
        f.platform.recognition.events.result("bonjour")
        assertEquals(listOf("bonjour"), f.commands)
    }

    @Test fun commandWindowExpiresBackToWakeMode() {
        val f = Fixture(); f.start()
        f.platform.recognition.events.result("Nexus")
        f.platform.speaker.events.finished(f.platform.speaker.utterances.last().second)
        f.platform.advance(12_000)
        f.platform.recognition.events.result("bonjour")
        assertTrue(f.commands.isEmpty())
    }

    @Test fun lateRecognitionCannotHearTheCompanionOrExecuteAfterSpeech() {
        val f = Fixture(); f.start()
        val old = f.platform.recognition
        assertTrue(f.session.speak("Bonjour !"))
        assertTrue(old.closed)
        old.events.result("Nexus supprimer mes notes")
        old.events.error(RecognitionFailure.TEMPORARY)
        f.platform.speaker.events.finished(f.platform.speaker.utterances.last().second)
        f.platform.advance(700)
        old.events.result("Nexus autre commande")
        assertTrue(f.commands.isEmpty())
        assertEquals(2, f.platform.totalStarts)
        f.platform.recognition.events.result("Nexus bonjour")
        assertEquals(listOf("bonjour"), f.commands)
    }

    @Test fun mouthOnlyOpensOnCurrentAudibleSpeechAndClosesOnCompletion() {
        val f = Fixture(); f.start()
        f.session.speak("Bonjour")
        val id = f.platform.speaker.utterances.last().second
        assertFalse(f.mouths.last())
        f.platform.speaker.events.started("stale")
        assertFalse(f.mouths.last())
        f.platform.speaker.events.started(id)
        assertTrue(f.mouths.last())
        f.platform.speaker.events.finished(id)
        assertFalse(f.mouths.last())
    }

    @Test fun flushedSpeechCallbacksDoNotResumeMicrophoneDuringNewReply() {
        val f = Fixture(); f.start()
        f.session.speak("Première réponse")
        val oldId = f.platform.speaker.utterances.last().second
        f.session.speak("Nouvelle réponse")
        val newId = f.platform.speaker.utterances.last().second
        f.platform.speaker.events.finished(oldId)
        f.platform.advance(1_000)
        assertEquals(1, f.platform.totalStarts)
        f.platform.speaker.events.finished(newId)
        f.platform.advance(700)
        assertEquals(2, f.platform.totalStarts)
    }

    @Test fun missingVoiceKeepsTheTwoTurnCommandFlowUsable() {
        val f = Fixture(); f.session.start(); f.platform.speaker.events.ready(false)
        f.platform.recognition.events.result("Nexus")
        f.platform.advance(500)
        f.platform.recognition.events.result("bonjour")
        assertEquals(listOf("bonjour"), f.commands)
        assertTrue(f.platform.speaker.utterances.isEmpty())
    }

    @Test fun rejectedSpeechResumesRecognition() {
        val f = Fixture(); f.start(); f.platform.acceptsSpeech = false
        assertFalse(f.session.speak("Bonjour"))
        f.platform.advance(700)
        assertEquals(2, f.platform.totalStarts)
        assertFalse(f.mouths.last())
    }

    @Test fun missingCompletionCallbackHasABoundedRecovery() {
        val f = Fixture(); f.start()
        f.session.speak("Bonjour")
        f.platform.advance(20_000)
        assertEquals(1, f.platform.speaker.stops)
        assertEquals(2, f.platform.totalStarts)
        assertFalse(f.mouths.last())
    }

    @Test fun stopDisposesEverythingAndInvalidatesAllOldCallbacks() {
        val f = Fixture(); f.start()
        val oldRecognizer = f.platform.recognition
        val oldSpeaker = f.platform.speaker
        f.session.speak("Bonjour")
        val id = oldSpeaker.utterances.last().second
        f.session.stop()
        oldSpeaker.events.ready(true); oldSpeaker.events.started(id); oldSpeaker.events.finished(id)
        oldRecognizer.events.result("Nexus bonjour")
        f.platform.advance(100_000)
        assertTrue(oldSpeaker.closed && oldRecognizer.closed)
        assertEquals(1, f.platform.totalStarts)
        assertEquals(listOf(true, false), f.sessions)
        assertFalse(f.mouths.last())
        assertTrue(f.commands.isEmpty())
    }

    @Test fun restartingCannotBeChangedByCallbacksFromThePreviousSession() {
        val f = Fixture(); f.start()
        val old = f.platform.speaker
        f.session.stop(); f.start()
        old.events.ready(false); old.events.interrupted()
        assertTrue(f.session.speak("Bonjour"))
        assertEquals(listOf(true, false, true), f.sessions)
    }

    @Test fun permanentRecognitionFailuresStopWithoutRetryStorm() {
        for (failure in listOf(RecognitionFailure.PERMISSION, RecognitionFailure.LANGUAGE_NOT_INSTALLED, RecognitionFailure.LANGUAGE_NOT_SUPPORTED)) {
            val f = Fixture(); f.start()
            f.platform.recognition.events.error(failure)
            f.platform.advance(100_000)
            assertEquals(listOf(true, false), f.sessions)
            assertEquals(1, f.platform.totalStarts)
            assertTrue(f.platform.speaker.closed)
        }
    }

    @Test fun realServiceFailuresRemainBounded() {
        val f = Fixture(); f.start()
        repeat(13) {
            f.platform.recognition.events.error(RecognitionFailure.TEMPORARY)
            f.platform.advance(3_000)
        }
        assertEquals(listOf(true, false), f.sessions)
        assertEquals(13, f.platform.totalStarts)
    }

    @Test fun recognizerCreationFailureCannotLeaveAnActiveSessionForever() {
        val f = Fixture(); f.platform.creationFails = true
        f.session.start(); f.platform.advance(100_000)
        assertEquals(listOf(true, false), f.sessions)
        assertTrue(f.platform.speaker.closed)
    }

    @Test fun unavailableServiceDoesNotStartTheMicrophoneOrTts() {
        val f = Fixture(); f.platform.supported = false
        f.session.start()
        assertTrue(f.sessions.isEmpty())
        assertTrue(f.platform.recognizers.isEmpty())
        assertTrue(f.platform.speakers.isEmpty())
    }

    @Test fun audioInterruptionStopsTheSession() {
        val f = Fixture(); f.start()
        f.platform.speaker.events.interrupted()
        f.platform.advance(100_000)
        assertEquals(listOf(true, false), f.sessions)
        assertEquals(1, f.platform.totalStarts)
    }

    @Test fun asynchronousBrainProcessingKeepsRecognitionPausedUntilReplyFinishes() {
        val f = Fixture(); f.start()
        val previous = f.platform.recognition
        f.session.beginResponse()
        f.platform.advance(2_000)
        previous.events.result("Nexus commande périmée")
        assertTrue(f.commands.isEmpty())
        assertEquals(1, f.platform.totalStarts)
        f.session.finishResponse("Bonjour")
        f.platform.advance(1_000)
        assertEquals(1, f.platform.totalStarts)
        f.platform.speaker.events.finished(f.platform.speaker.utterances.last().second)
        f.platform.advance(700)
        assertEquals(2, f.platform.totalStarts)
    }

    @Test fun workerFailureResumesListeningWithoutSpeaking() {
        val f = Fixture(); f.start()
        f.session.beginResponse(); f.session.finishResponse(null)
        f.platform.advance(500)
        assertEquals(2, f.platform.totalStarts)
        assertTrue(f.platform.speaker.utterances.isEmpty())
    }

    @Test fun lostWorkerCallbackHasABoundedRecoveryAndStopCancelsIt() {
        val f = Fixture(); f.start()
        f.session.beginResponse(); f.platform.advance(10_500)
        assertEquals(2, f.platform.totalStarts)
        f.session.beginResponse(); f.session.stop(); f.platform.advance(20_000)
        assertEquals(2, f.platform.totalStarts)
        f.start()
        assertEquals(3, f.platform.totalStarts)
    }

    @Test fun stoppedMicrophoneCannotBeReactivatedByALateBrainReply() {
        val f = Fixture(); f.start()
        f.session.beginResponse(); f.session.stop(); f.session.finishResponse("Réponse tardive")
        f.platform.advance(20_000)
        assertEquals(1, f.platform.totalStarts)
        assertTrue(f.platform.speaker.utterances.isEmpty())
        assertEquals(listOf(true, false), f.sessions)
    }
}
