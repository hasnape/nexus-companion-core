package fr.nexuscompanion.android

import java.io.IOException
import java.util.concurrent.Executor
import kotlin.math.log10

internal interface PcmRecorder : AutoCloseable {
    fun start()
    fun read(samples: ShortArray): Int
    fun stop()
}

internal interface LocalSpeechDecoder : AutoCloseable {
    fun accept(samples: ShortArray, count: Int): Boolean
    fun partial(): String
    fun result(): String
    fun finish(): String
    fun hypotheses(final: Boolean = false): List<SpeechHypothesis> = listOf(SpeechHypothesis(if (final) finish() else result())).filter { it.text.isNotBlank() }
}

/** One finite capture. The worker releases the microphone before delivering a final result. */
internal class EmbeddedSpeechCapture(
    private val worker: Executor,
    private val dispatch: (() -> Unit) -> Unit,
    private val openRecorder: () -> PcmRecorder,
    private val openDecoder: () -> LocalSpeechDecoder,
    private val events: RecognitionEvents
) : VoiceRecognition {
    private val recorderLock = Any()
    @Volatile private var cancelled = false
    private var activeRecorder: PcmRecorder? = null
    override val preparationTimeoutMs = 30_000L
    override val nextTurnDelayMs = 0L

    override fun start() {
        worker.execute { capture() }
    }

    override fun close() {
        cancelled = true
        synchronized(recorderLock) { activeRecorder?.let(::stopRecorder) }
    }

    private fun emit(action: () -> Unit) {
        dispatch { if (!cancelled) action() }
    }

    private fun capture() {
        if (cancelled) return
        val decoder = try {
            openDecoder()
        } catch (_: Exception) {
            emit { events.error(RecognitionFailure.MODEL_UNAVAILABLE) }
            return
        } catch (_: LinkageError) {
            emit { events.error(RecognitionFailure.MODEL_UNAVAILABLE) }
            return
        }
        try {
            val hypotheses = decoder.use { record(it) }
            if (hypotheses != null) emit {
                if (hypotheses.isEmpty()) events.error(RecognitionFailure.SILENCE)
                else events.alternatives(hypotheses)
            }
        } catch (_: SecurityException) {
            emit { events.error(RecognitionFailure.PERMISSION) }
        } catch (_: IOException) {
            emit { events.error(RecognitionFailure.TEMPORARY) }
        } catch (_: RuntimeException) {
            emit { events.error(RecognitionFailure.TEMPORARY) }
        }
    }

    /** Null means cancelled. An empty result means the decoder heard no usable phrase. */
    private fun record(decoder: LocalSpeechDecoder): List<SpeechHypothesis>? {
        if (cancelled) return null
        val recorder = openRecorder()
        try {
            synchronized(recorderLock) {
                if (cancelled) return null
                activeRecorder = recorder
                recorder.start()
            }
            return decode(recorder, decoder)
        } finally {
            synchronized(recorderLock) {
                activeRecorder = null
                stopRecorder(recorder)
                recorder.close()
            }
        }
    }

    private fun stopRecorder(recorder: PcmRecorder) {
        try { recorder.stop() } catch (_: RuntimeException) { /* capture may already have stopped */ }
    }

    private fun decode(recorder: PcmRecorder, decoder: LocalSpeechDecoder): List<SpeechHypothesis>? {
        val samples = ShortArray(FRAME_SAMPLES)
        var received = 0
        var firstSpeech = -1
        var previousPartial = ""
        var heldPrefix: SpeechHypothesis? = null
        var prefixEnded = 0
        var continuationStarted = false
        fun completed(next: List<SpeechHypothesis>): List<SpeechHypothesis> {
            val prefix = heldPrefix ?: return next
            val current = next.firstOrNull() ?: return if (continuationStarted) emptyList() else listOf(prefix)
            // An endpoint join must never turn an unrelated "yes" into approval.
            if (VoiceTranscriptPolicy.confirmation(current.text) == true) return listOf(prefix)
            if (VoiceTranscriptPolicy.parse(current.text).hasWake || VoiceTranscriptPolicy.isStopCommand(current.text) ||
                VoiceTranscriptPolicy.confirmation(current.text) == false) return next
            val text = "${prefix.text} ${current.text}".trim()
            if (text.length > NativeCapacity.MAX_MESSAGE_CHARS) return emptyList()
            val scores = listOfNotNull(prefix.confidence, current.confidence).filter { it.isFinite() && it in 0f..1f }
            return listOf(SpeechHypothesis(text, scores.minOrNull()))
        }
        while (!cancelled) {
            val count = recorder.read(samples)
            if (cancelled) return null
            if (count !in 1..samples.size) throw IOException("Microphone did not deliver PCM")
            if (received == 0) emit { events.ready() }
            received += count
            val level = inputLevel(samples, count)
            emit { events.level(level) }

            if (decoder.accept(samples, count)) {
                val final = decoder.hypotheses()
                if (final.isNotEmpty()) {
                    if (heldPrefix != null) return completed(final)
                    val best = final.first()
                    if (VoiceTranscriptPolicy.isIncompleteSearch(best.text)) {
                        heldPrefix = best
                        prefixEnded = received
                        if (firstSpeech < 0) firstSpeech = received
                        previousPartial = ""
                        emit { events.partial(best.text) }
                    } else return final
                } else if (heldPrefix != null && continuationStarted) return completed(emptyList())
            } else {
                val partial = decoder.partial().trim()
                val stalePrefix = heldPrefix?.let {
                    VoiceTranscriptPolicy.commandWords(it.text) == VoiceTranscriptPolicy.commandWords(partial)
                } == true
                if (partial.isNotEmpty() && partial != previousPartial && !stalePrefix) {
                    if (firstSpeech < 0) firstSpeech = received
                    previousPartial = partial
                    continuationStarted = heldPrefix != null
                    val displayed = heldPrefix?.let { "${it.text} $partial" } ?: partial
                    emit { events.partial(displayed) }
                }
            }
            if (heldPrefix != null && !continuationStarted && received - prefixEnded >= SAMPLE_RATE * 3 / 2) return listOfNotNull(heldPrefix)
            if (firstSpeech < 0 && received >= SAMPLE_RATE * 20) return decoder.hypotheses(final = true)
            if (firstSpeech >= 0 && received - firstSpeech >= SAMPLE_RATE * NativeCapacity.SPEECH_SECONDS) return completed(decoder.hypotheses(final = true))
        }
        return null
    }

    private fun inputLevel(samples: ShortArray, count: Int): Float {
        var power = 0.0
        for (index in 0 until count) {
            val sample = samples[index] / 32768.0
            power += sample * sample
        }
        val dbfs = 10.0 * log10((power / count).coerceAtLeast(1e-8))
        return (((dbfs + 60.0) / 60.0).coerceIn(0.0, 1.0) * 14.0 - 2.0).toFloat()
    }

    companion object {
        const val SAMPLE_RATE = 16_000
        const val FRAME_SAMPLES = 1_600
    }
}
