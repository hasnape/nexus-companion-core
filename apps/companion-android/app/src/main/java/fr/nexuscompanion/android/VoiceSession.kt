package fr.nexuscompanion.android

internal fun interface VoiceTask { fun cancel() }

internal enum class RecognitionFailure {
    SILENCE, NO_MATCH, PERMISSION, LANGUAGE_NOT_SUPPORTED, LANGUAGE_NOT_INSTALLED, TEMPORARY
}

internal interface VoiceRecognition {
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
}

internal interface SpeechEvents {
    fun ready(available: Boolean)
    fun started(id: String)
    fun finished(id: String)
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
    private val onSessionState: (Boolean) -> Unit
) {
    private var enabled = false
    private var listening = false
    private var speechReady = false
    private var awaitingCommand = false
    private var processing = false
    private var generation = 0L
    private var recognitionGeneration = 0L
    private var utteranceSequence = 0L
    private var utterance: String? = null
    private var failures = 0
    private var recognition: VoiceRecognition? = null
    private var speech: VoiceSpeech? = null
    private var restart: VoiceTask? = null
    private var commandTimeout: VoiceTask? = null
    private var speechTimeout: VoiceTask? = null
    private var responseTimeout: VoiceTask? = null
    private val wake = Regex("^(nexus|nexusse|nexuce)(?=$|[\\s,;:!?.…])([\\s,;:!?.…]+)?", RegexOption.IGNORE_CASE)

    fun available(): Boolean = try { platform.available() } catch (_: RuntimeException) { false }

    fun start() {
        if (enabled) return
        if (!available()) {
            onStatus("Reconnaissance hors ligne indisponible sur cet appareil. Le texte reste disponible.")
            return
        }
        enabled = true
        val session = ++generation
        failures = 0
        onSessionState(true)
        try {
            speech = platform.speech(object : SpeechEvents {
                override fun ready(available: Boolean) {
                    if (!enabled || generation != session) return
                    speechReady = available
                    if (!available) onStatus("Voix française hors ligne absente. Les réponses restent affichées.")
                }
                override fun started(id: String) {
                    if (!enabled || generation != session || utterance != id) return
                    onStatus("Nexus parle — micro en pause")
                    onSpeechState(true)
                }
                override fun finished(id: String) {
                    if (generation == session) speechFinished(id)
                }
                override fun interrupted() {
                    if (!enabled || generation != session) return
                    stop()
                    onStatus("Voix interrompue par Android. Réactive le micro quand tu es prêt.")
                }
            })
        } catch (_: RuntimeException) {
            onStatus("Voix locale indisponible. Les réponses restent affichées.")
        }
        onStatus("Écoute locale activée : dis « Nexus ».")
        beginRecognition()
    }

    private fun beginRecognition() {
        restart = null
        if (!enabled || utterance != null || listening || processing) return
        try {
            if (recognition == null) {
                val session = generation
                val turn = ++recognitionGeneration
                fun current() = enabled && generation == session && recognitionGeneration == turn && listening && utterance == null
                recognition = platform.recognizer(object : RecognitionEvents {
                    override fun ready() {
                        if (current()) onStatus(if (awaitingCommand) "Micro actif : je t'écoute." else "Micro actif : dis « Nexus ».")
                    }
                    override fun result(text: String) {
                        if (!current()) return
                        listening = false
                        failures = 0
                        acceptTranscript(text)
                        if (utterance == null) scheduleRestart()
                    }
                    override fun error(failure: RecognitionFailure) {
                        if (!current()) return
                        listening = false
                        when (failure) {
                            RecognitionFailure.SILENCE, RecognitionFailure.NO_MATCH -> scheduleRestart()
                            RecognitionFailure.TEMPORARY -> {
                                closeRecognition()
                                scheduleRestart(failed = true)
                            }
                            else -> {
                                stop()
                                onStatus(when (failure) {
                                    RecognitionFailure.PERMISSION -> "Micro non autorisé. Vérifie l'autorisation dans Android."
                                    RecognitionFailure.LANGUAGE_NOT_INSTALLED -> "Français hors ligne non installé. Installe-le dans le service vocal Android."
                                    else -> "Français non pris en charge par le service vocal hors ligne de cet appareil."
                                })
                            }
                        }
                    }
                })
            }
            listening = true
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
        val text = transcript.trim()
        if (text.isBlank()) return
        val prefix = wake.find(text)
        if (prefix == null && !awaitingCommand) return
        val command = if (prefix != null) text.substring(prefix.range.last + 1).trim() else text
        commandTimeout?.cancel()
        commandTimeout = null
        if (command.isBlank()) {
            awaitingCommand = true
            if (!speak("Je t'écoute.")) armCommandTimeout()
        } else {
            awaitingCommand = false
            onCommand(command)
        }
    }

    private fun armCommandTimeout() {
        commandTimeout?.cancel()
        if (!enabled || !awaitingCommand) return
        val session = generation
        commandTimeout = platform.after(12_000L) {
            if (enabled && generation == session) {
                awaitingCommand = false
                onStatus("Dis « Nexus » quand tu veux me parler.")
            }
        }
    }

    private fun scheduleRestart(failed: Boolean = false, delayMs: Long = 500L) {
        if (!enabled || utterance != null || listening || processing) return
        failures = if (failed) failures + 1 else 0
        if (failures > 12) {
            stop()
            onStatus("Le service vocal ne répond pas. Relance le micro ou utilise le texte.")
            return
        }
        restart?.cancel()
        val session = generation
        val delay = if (failed) (500L * failures).coerceAtMost(3_000L) else delayMs
        restart = platform.after(delay) { if (enabled && generation == session) beginRecognition() }
    }

    /** Keep the microphone closed while the local worker prepares its answer. */
    fun beginResponse() {
        if (!enabled) return
        processing = true
        awaitingCommand = false
        restart?.cancel(); restart = null
        commandTimeout?.cancel(); commandTimeout = null
        responseTimeout?.cancel()
        speechTimeout?.cancel(); speechTimeout = null
        utterance = null
        closeRecognition()
        try { speech?.stop() } catch (_: RuntimeException) { /* engine already gone */ }
        onSpeechState(false)
        val session = generation
        responseTimeout = platform.after(10_000L) {
            if (enabled && generation == session && processing) {
                processing = false
                responseTimeout = null
                scheduleRestart()
                onStatus("La réponse prend du temps. Retour à l'écoute locale.")
            }
        }
    }

    fun finishResponse(text: String?) {
        if (!enabled) return
        processing = false
        responseTimeout?.cancel(); responseTimeout = null
        if (text == null || !speak(text)) scheduleRestart()
    }

    /** Returns false when the local voice is absent or still initializing. */
    fun speak(text: String): Boolean {
        if (!enabled || text.isBlank()) return false
        if (!speechReady || speech == null) {
            onStatus("Réponse affichée. Voix locale absente ou en cours de préparation.")
            return false
        }
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
        onStatus("Nexus prépare sa réponse — micro en pause")
        val accepted = try { speech?.speak(text, id) == true } catch (_: RuntimeException) { false }
        if (!accepted) {
            speechFinished(id)
            onStatus("Réponse affichée. La voix locale n'a pas pu démarrer.")
            return false
        }
        speechTimeout = platform.after((15_000L + text.length * 100L).coerceAtMost(90_000L)) {
            if (utterance == id) {
                speechFinished(id)
                try { speech?.stop() } catch (_: RuntimeException) { /* engine already gone */ }
                onStatus("Réponse vocale interrompue. Retour à l'écoute locale.")
            }
        }
        return true
    }

    private fun speechFinished(id: String) {
        if (!enabled || utterance != id) return
        speechTimeout?.cancel()
        speechTimeout = null
        utterance = null
        onSpeechState(false)
        // Start the command window after the acknowledgement, not while TTS is speaking.
        armCommandTimeout()
        scheduleRestart(delayMs = 700L)
    }

    private fun closeRecognition() {
        ++recognitionGeneration
        listening = false
        val previous = recognition
        recognition = null
        try { previous?.close() } catch (_: RuntimeException) { /* callbacks already invalidated */ }
    }

    fun stop() {
        val wasEnabled = enabled
        enabled = false
        ++generation
        restart?.cancel()
        commandTimeout?.cancel()
        speechTimeout?.cancel()
        responseTimeout?.cancel()
        restart = null
        commandTimeout = null
        speechTimeout = null
        responseTimeout = null
        processing = false
        awaitingCommand = false
        utterance = null
        speechReady = false
        closeRecognition()
        val previous = speech
        speech = null
        try { previous?.close() } catch (_: RuntimeException) { /* all callbacks invalidated */ }
        onSpeechState(false)
        if (wasEnabled) onSessionState(false)
        onStatus("Micro désactivé")
    }
}
