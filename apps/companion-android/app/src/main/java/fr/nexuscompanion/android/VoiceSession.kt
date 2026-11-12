package fr.nexuscompanion.android

private const val DEFAULT_RESTART_DELAY_MS = 500L
private const val POST_PLAYBACK_RESTART_DELAY_MS = 200L
private const val INTERRUPTED_PLAYBACK_RESTART_DELAY_MS = 700L

internal fun interface VoiceTask { fun cancel() }

internal enum class RecognitionFailure {
    SILENCE, NO_MATCH, PERMISSION, LANGUAGE_NOT_SUPPORTED, LANGUAGE_NOT_INSTALLED, MODEL_UNAVAILABLE, TEMPORARY, BUSY, RATE_LIMIT
}

internal enum class SpeechOutputState {
    IDLE, PREPARING, PLAYING, COMPLETED, UNAVAILABLE, FAILED, INTERRUPTED
}

internal interface VoiceRecognition {
    val preparationTimeoutMs: Long get() = 8_000L
    /** Delay before preparing the next capture after a normal recognition result or silence. */
    val nextTurnDelayMs: Long get() = DEFAULT_RESTART_DELAY_MS
    fun start()
    fun close()
}

internal interface VoiceSpeech {
    fun speak(text: String, id: String): Boolean
    fun stop()
    fun close()
}

internal interface RecognitionEvents {
    fun ready()
    fun result(text: String)
    fun error(failure: RecognitionFailure)
    fun alternatives(hypotheses: List<SpeechHypothesis>) { result(hypotheses.firstOrNull()?.text.orEmpty()) }
    fun partial(text: String) = Unit
    fun speechStarted() = Unit
    fun speechEnded() = Unit
    fun level(rmsDb: Float) = Unit
}

internal interface SpeechEvents {
    fun ready(available: Boolean)
    fun started(id: String)
    fun finished(id: String)
    fun failed(id: String)
    fun interrupted()
}

/** Platform callbacks and public operations run on the main thread. */
internal interface VoicePlatform {
    fun available(): Boolean
    fun recognizer(events: RecognitionEvents): VoiceRecognition
    fun speech(events: SpeechEvents): VoiceSpeech
    fun after(delayMs: Long, action: () -> Unit): VoiceTask
}

/** Foreground voice orchestration, independent of Android services for regression tests. */
internal class VoiceSession(
    private val platform: VoicePlatform,
    private val onStatus: (String) -> Unit,
    private val onCommand: (String) -> Unit,
    private val onSpeechState: (Boolean) -> Unit,
    private val onSessionState: (Boolean) -> Unit,
    private val onTranscript: (String, Boolean) -> Unit = { _, _ -> },
    private val onInputLevel: (Int) -> Unit = {},
    private val onSpeechOutput: (SpeechOutputState) -> Unit = {},
    private val onMicrophoneReady: (Boolean) -> Unit = {}
) {
    private var enabled = false
    private var bodyMoving = false
    fun setBodyMoving(active: Boolean) { bodyMoving = active }
    private var playbackEnabled = true
    private var listening = false
    private var speechReady: Boolean? = null
    private var awaitingCommand = false
    private var processing = false
    private var capturing = false
    private var commandTimerPending = false
    private var generation = 0L
    private var recognitionGeneration = 0L
    private var recognitionCycle = 0L
    private var utteranceSequence = 0L
    private var utterance: String? = null
    private var failures = 0
    private var recognition: VoiceRecognition? = null
    private var speech: VoiceSpeech? = null
    private var pendingReply: String? = null
    private var replyFollowUp = false
    private var speechSetupTimeout: VoiceTask? = null
    private var restart: VoiceTask? = null
    private var commandTimeout: VoiceTask? = null
    private var speechTimeout: VoiceTask? = null
    private var responseTimeout: VoiceTask? = null
    private var recognitionTimeout: VoiceTask? = null
    private var transcriptTimeout: VoiceTask? = null

    fun available(): Boolean = try { platform.available() } catch (_: RuntimeException) { false }

    fun start() {
        if (enabled) return
        if (!available()) {
            onStatus("Reconnaissance hors ligne indisponible sur cet appareil. Le texte reste disponible.")
            return
        }
        enabled = true
        failures = 0
        onSessionState(true)
        if (playbackEnabled) prepareSpeech()
        onStatus("Écoute locale activée : dis « Nexus ».")
        beginRecognition()
    }

    private fun prepareSpeech() {
        if (speech != null) return
        val session = generation
        speechReady = null
        try {
            speech = platform.speech(object : SpeechEvents {
                override fun ready(available: Boolean) {
                    if (generation != session) return
                    speechReady = available
                    val reply = pendingReply
                    clearPendingReply()
                    if (!playbackEnabled) return
                    if (!available) {
                        replyFollowUp = false
                        onSpeechOutput(SpeechOutputState.UNAVAILABLE)
                        if (reply != null) scheduleRestart()
                    } else if (reply != null) {
                        speak(reply)
                    }
                }
                override fun started(id: String) {
                    if (generation != session || utterance != id) return
                    if (enabled) onStatus("Nexus parle — micro en pause")
                    onSpeechState(true)
                    onSpeechOutput(SpeechOutputState.PLAYING)
                }
                override fun finished(id: String) {
                    if (generation == session) speechFinished(id)
                }
                override fun failed(id: String) {
                    if (generation == session) speechFinished(id, SpeechOutputState.FAILED)
                }
                override fun interrupted() {
                    if (generation != session) return
                    val wasListening = enabled
                    stop()
                    onSpeechOutput(SpeechOutputState.INTERRUPTED)
                    if (wasListening) onStatus("Voix interrompue par Android. Réactive le micro quand tu es prêt.")
                }
            })
        } catch (_: RuntimeException) {
            speechReady = false
            onSpeechOutput(SpeechOutputState.UNAVAILABLE)
        }
    }

    private fun beginRecognition() {
        restart = null
        if (!enabled || utterance != null || pendingReply != null || listening || processing) return
        try {
            if (recognition == null) {
                val session = generation
                val turn = ++recognitionGeneration
                fun current() = enabled && generation == session && recognitionGeneration == turn && listening && utterance == null
                recognition = platform.recognizer(object : RecognitionEvents {
                    override fun ready() {
                        if (!current() || capturing) return
                        armRecognitionTimeout(45_000L)
                        if (commandTimerPending) armCommandTimeout()
                        onMicrophoneReady(true)
                        onStatus(if (awaitingCommand) "Micro actif : je t'écoute." else "Micro actif : dis « Nexus ».")
                    }
                    override fun result(text: String) {
                        alternatives(listOf(SpeechHypothesis(text)))
                    }
                    override fun alternatives(hypotheses: List<SpeechHypothesis>) {
                        if (!current()) return
                        val nextTurnDelay = recognition?.nextTurnDelayMs ?: DEFAULT_RESTART_DELAY_MS
                        // A completed client must never forward a late vendor event into the next capture.
                        closeRecognition()
                        failures = 0
                        val text = if (bodyMoving && hypotheses.firstOrNull()?.text?.let(VoiceTranscriptPolicy::isStopCommand) == true)
                            hypotheses.first().text else VoiceTranscriptPolicy.select(hypotheses, awaitingCommand)
                        if (text != null) {
                            showTranscript(text, true)
                            acceptTranscript(text)
                        } else if ((awaitingCommand || hypotheses.firstOrNull()?.text?.let(VoiceTranscriptPolicy::parse)?.hasWake == true)
                            && VoiceTranscriptPolicy.uncertainConfirmation(hypotheses.firstOrNull())) {
                            commandTimerPending = true
                            playReply("Je n’ai pas bien entendu ta confirmation. Peux-tu répéter ?", expectAnswer = true)
                        }
                        if (awaitingCommand && utterance == null && capturing && !commandTimerPending) armCommandTimeout()
                        if (utterance == null) scheduleRestart(delayMs = nextTurnDelay)
                    }
                    override fun speechStarted() { if (current()) markSpeechStarted() }
                    override fun level(rmsDb: Float) {
                        if (current() && rmsDb.isFinite()) onInputLevel(((rmsDb + 2f) * 100f / 14f).toInt().coerceIn(0, 100))
                    }
                    override fun partial(text: String) {
                        if (!current() || text.isBlank()) return
                        markSpeechStarted()
                        showTranscript(text, false)
                    }
                    override fun speechEnded() {
                        if (!current()) return
                        markSpeechStarted()
                        armRecognitionTimeout(8_000L)
                        onMicrophoneReady(false)
                        onStatus("Analyse locale de ta phrase…")
                    }
                    override fun error(failure: RecognitionFailure) {
                        if (!current()) return
                        val nextTurnDelay = recognition?.nextTurnDelayMs ?: DEFAULT_RESTART_DELAY_MS
                        closeRecognition()
                        if (awaitingCommand && capturing) armCommandTimeout()
                        when (failure) {
                            RecognitionFailure.SILENCE, RecognitionFailure.NO_MATCH -> scheduleRestart(delayMs = nextTurnDelay)
                            RecognitionFailure.TEMPORARY -> scheduleRestart(failed = true)
                            RecognitionFailure.BUSY, RecognitionFailure.RATE_LIMIT -> {
                                scheduleRestart(failed = true, delayMs = if (failure == RecognitionFailure.RATE_LIMIT) 15_000L else 1_500L)
                                if (enabled) onStatus(if (failure == RecognitionFailure.RATE_LIMIT)
                                    "Android limite les demandes vocales — reprise dans 15 secondes."
                                    else "Service vocal occupé — reprise dans un instant.")
                            }
                            else -> {
                                stop()
                                onStatus(when (failure) {
                                    RecognitionFailure.PERMISSION -> "Micro non autorisé. Vérifie l'autorisation dans Android."
                                    RecognitionFailure.LANGUAGE_NOT_INSTALLED -> "Français hors ligne non installé. Installe-le dans le service vocal Android."
                                    RecognitionFailure.MODEL_UNAVAILABLE -> "Le modèle français intégré ne s’est pas chargé. Relance Nexus pour réessayer."
                                    else -> "Français non pris en charge par le service vocal hors ligne de cet appareil."
                                })
                            }
                        }
                    }
                })
            }
            capturing = false
            onInputLevel(0)
            ++recognitionCycle
            listening = true
            onStatus("Préparation du micro…")
            armRecognitionTimeout(recognition?.preparationTimeoutMs ?: 8_000L)
            recognition?.start()
        } catch (_: SecurityException) {
            stop()
            onStatus("Micro non autorisé. Le texte reste disponible.")
        } catch (_: RuntimeException) {
            closeRecognition()
            scheduleRestart(failed = true)
        }
    }

    private fun acceptTranscript(transcript: String) {
        if (transcript.isBlank() || transcript.length > NativeCapacity.MAX_MESSAGE_CHARS) return
        val parsed = VoiceTranscriptPolicy.parse(transcript)
        if (!parsed.hasWake && !awaitingCommand && !(bodyMoving && VoiceTranscriptPolicy.isStopCommand(transcript))) return
        val command = parsed.command
        commandTimeout?.cancel()
        commandTimeout = null
        if (command.isBlank()) {
            awaitingCommand = true
            commandTimerPending = true
            speak("Je t'écoute.")
        } else {
            awaitingCommand = false
            commandTimerPending = false
            onCommand(command)
        }
    }

    private fun armCommandTimeout() {
        commandTimerPending = false
        commandTimeout?.cancel()
        commandTimeout = null
        if (!enabled || !awaitingCommand) return
        val session = generation
        commandTimeout = platform.after(NativeCapacity.COMMAND_WAIT_MS.toLong()) {
            if (enabled && generation == session) {
                commandTimeout = null
                awaitingCommand = false
                onStatus("Dis « Nexus » quand tu veux me parler.")
            }
        }
    }

    private fun markSpeechStarted() {
        if (capturing) return
        capturing = true
        // The command window limits the wait for speech, never the user's whole sentence.
        if (awaitingCommand) {
            commandTimerPending = false
            commandTimeout?.cancel(); commandTimeout = null
        }
        armRecognitionTimeout(NativeCapacity.RECOGNITION_TIMEOUT_MS.toLong())
        onMicrophoneReady(true)
        onStatus("Je t'écoute — parle naturellement.")
    }

    private fun armRecognitionTimeout(delayMs: Long) {
        recognitionTimeout?.cancel()
        val session = generation
        val cycle = recognitionCycle
        recognitionTimeout = platform.after(delayMs) {
            if (enabled && generation == session && recognitionCycle == cycle && listening) {
                if (awaitingCommand && capturing) armCommandTimeout()
                closeRecognition()
                scheduleRestart(failed = true)
            }
        }
    }

    private fun showTranscript(text: String, final: Boolean) {
        transcriptTimeout?.cancel()
        onTranscript(text.take(NativeCapacity.MAX_MESSAGE_CHARS), final)
        val session = generation
        transcriptTimeout = platform.after(15_000L) {
            if (enabled && generation == session) onTranscript("", false)
        }
    }

    /** Explicit direct dictation bypasses the wake phrase without persisting anything. */
    fun listenForCommand() {
        if (!enabled || processing) return
        restart?.cancel(); restart = null
        commandTimeout?.cancel(); commandTimeout = null
        closeRecognition()
        stopPlayback()
        awaitingCommand = true
        commandTimerPending = true
        beginRecognition()
    }

    private fun scheduleRestart(failed: Boolean = false, delayMs: Long = DEFAULT_RESTART_DELAY_MS) {
        if (!enabled || utterance != null || pendingReply != null || listening || processing) return
        onInputLevel(0)
        failures = if (failed) failures + 1 else 0
        if (failures > 12) {
            stop()
            onStatus("Le service vocal ne répond pas. Relance le micro ou utilise le texte.")
            return
        }
        restart?.cancel()
        onStatus(if (failed) "Le micro se réinitialise…" else "Reprise de l'écoute locale…")
        val session = generation
        val delay = if (failed) (500L * failures).coerceAtMost(3_000L).coerceAtLeast(delayMs) else delayMs
        restart = platform.after(delay) { if (enabled && generation == session) beginRecognition() }
    }

    /** Keep the microphone closed while the local worker prepares its answer. */
    fun beginResponse() {
        stopPlayback()
        if (!enabled) return
        processing = true
        awaitingCommand = false
        commandTimerPending = false
        restart?.cancel(); restart = null
        commandTimeout?.cancel(); commandTimeout = null
        responseTimeout?.cancel()
        closeRecognition()
        onStatus("Nexus réfléchit — micro en pause")
        val session = generation
        responseTimeout = platform.after(NativeCapacity.RESPONSE_TIMEOUT_MS.toLong()) {
            if (enabled && generation == session && processing) {
                processing = false
                responseTimeout = null
                scheduleRestart()
                onStatus("La réponse prend du temps. Retour à l'écoute locale.")
            }
        }
    }

    fun finishResponse(text: String?, expectAnswer: Boolean = false) {
        if (!enabled) return
        processing = false
        responseTimeout?.cancel(); responseTimeout = null
        if (text == null) scheduleRestart() else playReply(text, expectAnswer)
    }

    /** Play an explicit reply or test phrase without activating recognition. */
    fun playReply(text: String, expectAnswer: Boolean = false) {
        if (utterance != null || pendingReply != null) stopPlayback()
        replyFollowUp = expectAnswer && enabled
        if (!playbackEnabled || text.isBlank()) {
            if (replyFollowUp) {
                awaitingCommand = true
                commandTimerPending = true
            }
            replyFollowUp = false
            scheduleRestart()
            return
        }
        prepareSpeech()
        when (speechReady) {
            true -> speak(text)
            false -> {
                replyFollowUp = false
                onSpeechOutput(SpeechOutputState.UNAVAILABLE)
                scheduleRestart()
            }
            null -> {
                restart?.cancel(); restart = null
                closeRecognition()
                pendingReply = text
                onSpeechOutput(SpeechOutputState.PREPARING)
                val session = generation
                speechSetupTimeout = platform.after(5_000L) {
                    if (generation == session && pendingReply != null) {
                        clearPendingReply()
                        replyFollowUp = false
                        onSpeechOutput(SpeechOutputState.FAILED)
                        scheduleRestart()
                    }
                }
            }
        }
    }

    fun setSpokenRepliesEnabled(active: Boolean) {
        if (playbackEnabled == active) return
        playbackEnabled = active
        if (active) {
            if (enabled) prepareSpeech()
            return
        }
        val playbackWasActive = utterance != null
        val replyWasPending = pendingReply != null
        stopPlayback()
        if (playbackWasActive || replyWasPending) {
            if (awaitingCommand) commandTimerPending = true
            scheduleRestart(delayMs = if (playbackWasActive) INTERRUPTED_PLAYBACK_RESTART_DELAY_MS else DEFAULT_RESTART_DELAY_MS)
        }
    }

    private fun clearPendingReply() {
        pendingReply = null
        speechSetupTimeout?.cancel()
        speechSetupTimeout = null
    }

    private fun stopPlayback() {
        replyFollowUp = false
        clearPendingReply()
        speechTimeout?.cancel(); speechTimeout = null
        utterance = null
        try { speech?.stop() } catch (_: RuntimeException) { /* engine already gone */ }
        onSpeechState(false)
        onSpeechOutput(SpeechOutputState.IDLE)
    }

    /** Returns false when the local voice is absent or still initializing. */
    fun speak(text: String): Boolean {
        if (!playbackEnabled || text.isBlank() || speechReady != true || speech == null) return false
        restart?.cancel()
        restart = null
        commandTimeout?.cancel()
        commandTimeout = null
        closeRecognition()
        speechTimeout?.cancel()
        responseTimeout?.cancel()
        val id = "nexus-${generation}-${++utteranceSequence}"
        utterance = id
        onSpeechState(false)
        if (enabled) onStatus("Nexus prépare sa réponse — micro en pause")
        onSpeechOutput(SpeechOutputState.PREPARING)
        val accepted = try { speech?.speak(text, id) == true } catch (_: RuntimeException) { false }
        if (!accepted) {
            speechFinished(id, SpeechOutputState.FAILED)
            return false
        }
        speechTimeout = platform.after((15_000L + text.length * 100L).coerceAtMost(90_000L)) {
            if (utterance == id) {
                speechFinished(id, SpeechOutputState.INTERRUPTED)
                try { speech?.stop() } catch (_: RuntimeException) { /* engine already gone */ }
            }
        }
        return true
    }

    private fun speechFinished(id: String, result: SpeechOutputState = SpeechOutputState.COMPLETED) {
        if (utterance != id) return
        speechTimeout?.cancel()
        speechTimeout = null
        utterance = null
        onSpeechState(false)
        onSpeechOutput(result)
        if (replyFollowUp && result == SpeechOutputState.COMPLETED && enabled) awaitingCommand = true
        replyFollowUp = false
        // Wait for the next ready callback: TTS completion does not mean the microphone is ready.
        if (awaitingCommand) commandTimerPending = true
        // After confirmed playback, leave a short margin for residual speaker sound.
        val restartDelay = if (result == SpeechOutputState.COMPLETED) POST_PLAYBACK_RESTART_DELAY_MS
            else INTERRUPTED_PLAYBACK_RESTART_DELAY_MS
        scheduleRestart(delayMs = restartDelay)
    }

    private fun closeRecognition() {
        recognitionTimeout?.cancel(); recognitionTimeout = null
        ++recognitionCycle
        ++recognitionGeneration
        listening = false
        onMicrophoneReady(false)
        onInputLevel(0)
        val previous = recognition
        recognition = null
        try { previous?.close() } catch (_: RuntimeException) { /* callbacks already invalidated */ }
    }

    fun stopListening() {
        bodyMoving = false
        val wasEnabled = enabled
        enabled = false
        restart?.cancel()
        commandTimeout?.cancel()
        responseTimeout?.cancel()
        transcriptTimeout?.cancel(); transcriptTimeout = null
        onTranscript("", false)
        restart = null
        commandTimeout = null
        responseTimeout = null
        processing = false
        awaitingCommand = false
        replyFollowUp = false
        commandTimerPending = false
        capturing = false
        closeRecognition()
        if (wasEnabled) onSessionState(false)
        onStatus("Micro désactivé")
    }

    fun stop() {
        ++generation
        clearPendingReply()
        speechTimeout?.cancel(); speechTimeout = null
        utterance = null
        speechReady = null
        val previous = speech
        speech = null
        stopListening()
        try { previous?.close() } catch (_: RuntimeException) { /* all callbacks invalidated */ }
        onSpeechState(false)
        onSpeechOutput(SpeechOutputState.IDLE)
    }
}
