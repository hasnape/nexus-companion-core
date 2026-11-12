package fr.nexuscompanion.android

import org.junit.Assert.*
import org.junit.Test

class VoiceSessionTest {
    @Test fun aWokenUncertainConfirmationAsksAgainWithoutExecutingOrReactingToAmbientApprovals() {
        val f = Fixture(); f.start()
        f.platform.recognition.events.alternatives(listOf(SpeechHypothesis("oui", .2f)))
        f.platform.advance(500)
        assertTrue(f.platform.speaker.utterances.isEmpty())
        f.platform.recognition.events.alternatives(listOf(SpeechHypothesis("Nexus oui cherche", .2f)))
        assertTrue(f.commands.isEmpty())
        assertTrue(f.platform.speaker.utterances.last().first.contains("Peux-tu répéter"))
    }
    @Test fun conversationalWindowAllowsAThoughtfulAnswerAfterFifteenSeconds() {
        val f = Fixture(); f.start()
        f.session.beginResponse(); f.session.finishResponse("Tu veux que je cherche ?", expectAnswer = true)
        f.platform.speaker.events.finished(f.platform.speaker.utterances.last().second)
        f.platform.advance(200 + 15_000L)
        f.platform.recognition.events.result("oui")
        assertEquals(listOf("oui"), f.commands)
    }
    @Test fun anUncertainShortConfirmationIsRepeatedInsteadOfExecuted() {
        val f = Fixture(); f.start()
        f.session.listenForCommand()
        f.platform.recognition.events.alternatives(listOf(SpeechHypothesis("oui", 0.2f)))
        assertTrue(f.commands.isEmpty())
        assertTrue(f.platform.speaker.utterances.last().first.contains("Peux-tu répéter"))
        f.platform.speaker.events.finished(f.platform.speaker.utterances.last().second)
        f.platform.advance(200)
        f.platform.recognition.events.alternatives(listOf(SpeechHypothesis("oui", 0.9f)))
        assertEquals(listOf("oui"), f.commands)
    }
    @Test fun stopWithoutWakeIsAvailableOnlyWhileTheBodyIsMoving() {
        val f = Fixture(); f.start()
        f.session.setBodyMoving(true)
        f.platform.recognition.events.result("conversation ambiante")
        f.platform.advance(500)
        assertTrue(f.commands.isEmpty())
        f.platform.recognition.events.result("stop")
        assertEquals(listOf("stop"), f.commands)
        f.session.setBodyMoving(false)
        f.platform.advance(500)
        f.platform.recognition.events.result("stop")
        assertEquals(1, f.commands.size)
    }
    @Test fun conversationalReplyAcceptsTheNextSentenceOnlyAfterPlaybackCompletes() {
        val f = Fixture(); f.start()
        val before = f.platform.recognition
        f.session.beginResponse()
        f.session.finishResponse("Tu veux que je cherche ?", expectAnswer = true)
        val id = f.platform.speaker.utterances.last().second
        before.events.result("oui")
        f.platform.advance(5_000)
        assertTrue(f.commands.isEmpty())
        assertTrue(f.platform.recognition.closed)
        f.platform.speaker.events.finished(id)
        f.platform.advance(200)
        f.platform.recognition.events.result("oui")
        assertEquals(listOf("oui"), f.commands)
    }

    @Test fun conversationalWindowStartsWhenTheMicrophoneIsReadyAndExpiresBackToWakeMode() {
        val f = Fixture(); f.start()
        f.platform.autoReady = false
        f.session.beginResponse()
        f.session.finishResponse("Une réponse", expectAnswer = true)
        f.platform.speaker.events.finished(f.platform.speaker.utterances.last().second)
        f.platform.advance(200 + 5_000L)
        f.platform.recognition.events.ready()
        f.platform.advance(NativeCapacity.COMMAND_WAIT_MS + 1L)
        f.platform.recognition.events.result("oui")
        assertTrue(f.commands.isEmpty())
        f.platform.advance(500)
        f.platform.recognition.events.result("Nexus bonjour")
        assertEquals(listOf("bonjour"), f.commands)
    }

    @Test fun failedOrStoppedPlaybackCannotOpenAConfirmationWindow() {
        for (stop in listOf(false, true)) {
            val f = Fixture(); f.start()
            f.session.beginResponse()
            f.session.finishResponse("Tu confirmes ?", expectAnswer = true)
            val id = f.platform.speaker.utterances.last().second
            if (stop) { f.session.stop(); f.start() } else f.platform.speaker.events.failed(id)
            f.platform.advance(700)
            f.platform.recognition.events.result("oui")
            assertTrue(f.commands.isEmpty())
        }
    }

    private class FakePlatform(private val localTurnDelayMs: Long?) : VoicePlatform {
        data class Task(val due: Long, val action: () -> Unit, var canceled: Boolean = false)
        inner class Recognition(val events: RecognitionEvents) : VoiceRecognition {
            var starts = 0
            var closed = false
            override val nextTurnDelayMs get() = localTurnDelayMs ?: super.nextTurnDelayMs
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
    private class Fixture(localTurnDelayMs: Long? = null) {
        val platform = FakePlatform(localTurnDelayMs)
        val commands = mutableListOf<String>()
        val statuses = mutableListOf<String>()
        val mouths = mutableListOf<Boolean>()
        val sessions = mutableListOf<Boolean>()
        val transcripts = mutableListOf<Pair<String, Boolean>>()
        val levels = mutableListOf<Int>()
        val output = mutableListOf<SpeechOutputState>()
        val session = VoiceSession(platform, statuses::add, commands::add, mouths::add, sessions::add,
            { text, final -> transcripts.add(text to final) }, levels::add, output::add)
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

    @Test fun previousSilentTurnCannotExecuteOrDisableTheNextTurn() {
        for (failure in listOf(RecognitionFailure.SILENCE, RecognitionFailure.NO_MATCH)) {
            val f = Fixture(); f.start()
            val previous = f.platform.recognition.events
            previous.error(failure)
            f.platform.advance(500)
            previous.partial("phrase périmée")
            previous.level(12f)
            previous.result("Nexus ancienne commande")
            previous.error(RecognitionFailure.PERMISSION)
            assertTrue(f.commands.isEmpty())
            assertTrue(f.transcripts.isEmpty())
            assertEquals(0, f.levels.last())
            assertEquals(listOf(true), f.sessions)
            f.platform.recognition.events.result("Nexus bonjour")
            assertEquals(listOf("bonjour"), f.commands)
        }
    }

    @Test fun localRecognitionRestartsOnTheNextDispatchAfterSilenceOrAmbientSpeech() {
        val f = Fixture(localTurnDelayMs = 0L); f.start()
        val finishTurns: List<(RecognitionEvents) -> Unit> = listOf(
            { it.error(RecognitionFailure.SILENCE) },
            { it.error(RecognitionFailure.NO_MATCH) },
            { it.result("conversation ambiante") }
        )
        for (finish in finishTurns) {
            val previous = f.platform.recognition
            val starts = f.platform.totalStarts
            finish(previous.events)
            assertTrue(previous.closed)
            assertEquals(starts, f.platform.totalStarts)
            f.platform.advance(0)
            assertEquals(starts + 1, f.platform.totalStarts)
        }
        assertTrue(f.commands.isEmpty())
    }

    @Test fun stoppingOnlyTheMicrophoneCancelsAQueuedLocalRestart() {
        val f = Fixture(localTurnDelayMs = 0L); f.start()
        f.platform.recognition.events.error(RecognitionFailure.SILENCE)
        f.session.stopListening()
        f.platform.advance(0)
        assertEquals(1, f.platform.totalStarts)
        assertEquals(listOf(true, false), f.sessions)
    }

    @Test fun stoppingOnlyTheMicrophoneAllowsTheCurrentReplyToFinish() {
        val f = Fixture(); f.start()
        f.session.playReply("Une réponse en cours")
        val id = f.platform.speaker.utterances.single().second
        f.platform.speaker.events.started(id)

        f.session.stopListening()

        assertEquals(listOf(true, false), f.sessions)
        assertFalse(f.platform.speaker.closed)
        assertTrue(f.mouths.last())
        f.platform.speaker.events.finished(id)
        f.platform.advance(200)
        assertEquals(1, f.platform.totalStarts)
        assertEquals(SpeechOutputState.COMPLETED, f.output.last())
        f.session.playReply("Une autre réponse")
        assertEquals(2, f.platform.speaker.utterances.size)
    }

    @Test fun mutingWhileTheVoiceInitializesCancelsPlaybackAndResumesListening() {
        val f = Fixture(); f.session.start()
        f.session.beginResponse()
        f.session.finishResponse("Une réponse en attente")
        assertEquals(SpeechOutputState.PREPARING, f.output.last())

        f.session.setSpokenRepliesEnabled(false)
        f.platform.speaker.events.ready(true)
        f.platform.advance(500)

        assertTrue(f.platform.speaker.utterances.isEmpty())
        assertEquals(2, f.platform.totalStarts)
        f.platform.recognition.events.result("Nexus")
        f.platform.advance(500)
        f.platform.recognition.events.result("bonjour")
        assertEquals(listOf("bonjour"), f.commands)
        assertTrue(f.platform.speaker.utterances.isEmpty())
    }

    @Test fun mutingAnActiveReplyStopsItBeforeResumingListening() {
        val f = Fixture(); f.start()
        f.session.playReply("Une réponse en cours")
        val id = f.platform.speaker.utterances.single().second
        f.platform.speaker.events.started(id)

        f.session.setSpokenRepliesEnabled(false)
        f.platform.speaker.events.finished(id)

        assertEquals(1, f.platform.speaker.stops)
        assertFalse(f.mouths.last())
        assertEquals(SpeechOutputState.IDLE, f.output.last())
        f.platform.advance(699)
        assertEquals(1, f.platform.totalStarts)
        f.platform.advance(1)
        assertEquals(2, f.platform.totalStarts)
        assertEquals(listOf(true), f.sessions)
    }

    @Test fun completedAmbientTurnCannotDeliverAnotherCommandAfterRestart() {
        val f = Fixture(); f.start()
        val previous = f.platform.recognition.events
        previous.result("conversation ambiante")
        f.platform.advance(499)
        assertEquals(1, f.platform.totalStarts)
        f.platform.advance(1)
        previous.result("Nexus ancienne commande")
        assertTrue(f.commands.isEmpty())
        assertEquals("conversation ambiante" to true, f.transcripts.last())
        f.platform.recognition.events.result("Nexus bonjour")
        assertEquals(listOf("bonjour"), f.commands)
        assertEquals(2, f.platform.recognizers.size)
        assertTrue(f.platform.recognizers.first().closed)
    }

    @Test fun wakeCommandWindowStartsWhenTheMicrophoneIsActuallyReady() {
        val f = Fixture(); f.start()
        f.platform.recognition.events.result("Nexus")
        f.platform.autoReady = false
        f.platform.speaker.events.finished(f.platform.speaker.utterances.last().second)
        f.platform.advance(6_700)
        f.platform.recognition.events.ready()
        f.platform.advance(10_000)
        f.platform.recognition.events.result("bonjour")
        assertEquals(listOf("bonjour"), f.commands)
    }

    @Test fun wakeWithoutTtsAlsoWaitsForTheMicrophoneToBeReady() {
        val f = Fixture(); f.session.start(); f.platform.speaker.events.ready(false)
        f.platform.autoReady = false
        f.platform.recognition.events.speechStarted()
        f.platform.recognition.events.result("Nexus")
        f.platform.advance(6_500)
        f.platform.recognition.events.ready()
        f.platform.advance(10_000)
        f.platform.recognition.events.result("bonjour")
        assertEquals(listOf("bonjour"), f.commands)
    }

    @Test fun androidRateLimitsUseACooldownInsteadOfRapidRestarts() {
        val f = Fixture(); f.start()
        f.platform.recognition.events.error(RecognitionFailure.RATE_LIMIT)
        f.platform.advance(14_999)
        assertEquals(1, f.platform.totalStarts)
        f.platform.advance(1)
        assertEquals(2, f.platform.totalStarts)
        assertTrue(f.statuses.any { "15 secondes" in it })
    }

    @Test fun busyRecognizerGetsTimeToReleaseItsAudioResources() {
        val f = Fixture(); f.start()
        val old = f.platform.recognition
        old.events.error(RecognitionFailure.BUSY)
        assertTrue(old.closed)
        f.platform.advance(1_499); assertEquals(1, f.platform.totalStarts)
        f.platform.advance(1); assertEquals(2, f.platform.totalStarts)
        old.events.result("Nexus ancien résultat")
        assertTrue(f.commands.isEmpty())
    }

    @Test fun stoppingCancelsRateLimitRecoveryAndSignalCallbacks() {
        val f = Fixture(); f.start()
        val old = f.platform.recognition
        old.events.level(8f); assertTrue(f.levels.last() > 0)
        old.events.error(RecognitionFailure.RATE_LIMIT)
        f.session.stop(); old.events.level(12f)
        f.platform.advance(100_000)
        assertEquals(1, f.platform.totalStarts)
        assertEquals(0, f.levels.last())
    }

    @Test fun signalMeterIsBoundedAndPausedDuringTheCompanionsVoice() {
        val f = Fixture(); f.start()
        val old = f.platform.recognition
        old.events.level(100f); assertEquals(100, f.levels.last())
        old.events.level(-100f); assertEquals(0, f.levels.last())
        old.events.level(Float.NaN); assertEquals(0, f.levels.last())
        f.session.speak("Bonjour"); old.events.level(12f)
        assertEquals(0, f.levels.last())
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
        repeat(NativeCapacity.COMMAND_WAIT_MS / 500 + 2) {
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
        repeat(NativeCapacity.RECOGNITION_TIMEOUT_MS / 10_000) { old.events.partial("Nexus en cours"); f.platform.advance(10_000) }
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
        f.platform.advance(700)
        f.platform.advance(NativeCapacity.COMMAND_WAIT_MS.toLong())
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

    @Test fun anExplicitReplyCanBeSpokenWithoutARecognitionService() {
        val f = Fixture(); f.platform.supported = false; f.session.start()
        f.session.playReply("Réponse écrite")
        assertEquals(SpeechOutputState.PREPARING, f.output.last())
        f.platform.speaker.events.ready(true)
        val (text, id) = f.platform.speaker.utterances.single()
        assertEquals("Réponse écrite", text)
        f.platform.speaker.events.started(id)
        assertEquals(SpeechOutputState.PLAYING, f.output.last())
        f.platform.speaker.events.finished(id)
        assertEquals(SpeechOutputState.COMPLETED, f.output.last())
        f.platform.advance(5_000)
        assertEquals(0, f.platform.totalStarts)
        assertTrue(f.sessions.isEmpty())
    }

    @Test fun replyWaitsForLocalVoiceWithoutReopeningTheMicrophone() {
        val f = Fixture(); f.session.start()
        f.session.beginResponse(); f.session.finishResponse("Bonjour")
        f.platform.advance(4_000)
        assertEquals(1, f.platform.totalStarts)
        assertTrue(f.platform.speaker.utterances.isEmpty())
        f.platform.speaker.events.ready(true)
        val (text, id) = f.platform.speaker.utterances.single()
        assertEquals("Bonjour", text)
        assertEquals(1, f.platform.totalStarts)
        f.platform.speaker.events.finished(id)
        f.platform.advance(700)
        assertEquals(2, f.platform.totalStarts)
    }

    @Test fun voiceInitializationTimeoutDiscardsTheReplyAndRestartsListening() {
        val f = Fixture(); f.session.start()
        f.session.beginResponse(); f.session.finishResponse("Réponse expirée")
        f.platform.advance(5_500)
        assertEquals(SpeechOutputState.FAILED, f.output.last())
        assertEquals(2, f.platform.totalStarts)
        f.platform.speaker.events.ready(true)
        assertTrue(f.platform.speaker.utterances.isEmpty())
    }

    @Test fun stoppingTheActivityCancelsAnExplicitReplyWaitingForSpeech() {
        val f = Fixture(); f.session.playReply("Réponse à annuler")
        val speaker = f.platform.speaker
        f.session.stop()
        speaker.events.ready(true)
        f.platform.advance(10_000)
        assertTrue(speaker.closed)
        assertTrue(speaker.utterances.isEmpty())
        assertEquals(SpeechOutputState.IDLE, f.output.last())
        assertEquals(0, f.platform.totalStarts)
    }

    @Test fun aMissingLocalVoiceKeepsItsOwnStatusWhenTheMicrophoneResumes() {
        val f = Fixture(); f.session.start(); f.platform.speaker.events.ready(false)
        f.session.beginResponse(); f.session.finishResponse("Bonjour")
        f.platform.advance(500)
        assertEquals(SpeechOutputState.UNAVAILABLE, f.output.last())
        assertTrue(f.platform.speaker.utterances.isEmpty())
        assertEquals(2, f.platform.totalStarts)
    }

    @Test fun aSpeechEngineErrorCannotBeReportedAsCompletedPlayback() {
        val f = Fixture(); f.start()
        f.session.beginResponse(); f.session.finishResponse("Bonjour")
        val id = f.platform.speaker.utterances.single().second
        f.platform.speaker.events.started(id)
        f.platform.speaker.events.failed(id)
        f.platform.speaker.events.finished(id)
        f.platform.advance(699)
        assertFalse(f.mouths.last())
        assertEquals(SpeechOutputState.FAILED, f.output.last())
        assertEquals(1, f.platform.totalStarts)
        f.platform.advance(1)
        assertEquals(2, f.platform.totalStarts)
    }

    @Test fun audioInterruptionStopsTheSession() {
        val f = Fixture(); f.start()
        f.platform.speaker.events.interrupted()
        f.platform.advance(100_000)
        assertEquals(listOf(true, false), f.sessions)
        assertEquals(1, f.platform.totalStarts)
    }

    @Test fun recognitionResumesTwoHundredMillisecondsAfterConfirmedPlayback() {
        val f = Fixture(); f.start()
        val previous = f.platform.recognition
        f.session.beginResponse()
        f.platform.advance(2_000)
        previous.events.result("Nexus commande périmée")
        assertTrue(f.commands.isEmpty())
        assertEquals(1, f.platform.totalStarts)
        f.session.finishResponse("Bonjour")
        val id = f.platform.speaker.utterances.single().second
        f.platform.speaker.events.started(id)
        f.platform.advance(1_000)
        assertEquals(1, f.platform.totalStarts)
        f.platform.speaker.events.finished(id)
        f.platform.advance(199)
        assertEquals(1, f.platform.totalStarts)
        f.platform.advance(1)
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
        f.session.beginResponse(); f.platform.advance(NativeCapacity.RESPONSE_TIMEOUT_MS + 500L)
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
