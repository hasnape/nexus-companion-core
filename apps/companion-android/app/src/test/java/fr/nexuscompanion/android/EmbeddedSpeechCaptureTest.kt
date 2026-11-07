package fr.nexuscompanion.android

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.concurrent.Executor
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class EmbeddedSpeechCaptureTest {
    @get:Rule val temporary = TemporaryFolder()

    private class Recorder(private val count: Int = 3200) : PcmRecorder {
        var started = false
        var stopped = false
        var closed = false
        override fun start() { started = true }
        override fun read(samples: ShortArray): Int {
            check(started && !stopped)
            if (count > 0) samples.fill(200)
            return count
        }
        override fun stop() { stopped = true }
        override fun close() { closed = true }
    }

    private class Decoder : LocalSpeechDecoder {
        private var frames = 0
        var closed = false
        override fun accept(samples: ShortArray, count: Int): Boolean {
            check(count > 0)
            return ++frames >= 2
        }
        override fun partial() = "bonjour"
        override fun result() = "bonjour comment vas tu"
        override fun finish() = "phrase longue"
        override fun close() { closed = true }
    }

    private class Fixture(
        val recorder: Recorder = Recorder(),
        private val decoderFails: Boolean = false
    ) {
        private val work = mutableListOf<Runnable>()
        private val callbacks = mutableListOf<() -> Unit>()
        val decoder = Decoder()
        val events = mutableListOf<String>()
        val capture = EmbeddedSpeechCapture(
            Executor { work.add(it) },
            dispatch = { callbacks.add(it) },
            openRecorder = { recorder },
            openDecoder = { if (decoderFails) throw IOException("Missing model"); decoder },
            events = object : RecognitionEvents {
                override fun ready() { events.add("ready") }
                override fun partial(text: String) { events.add("partial:$text") }
                override fun result(text: String) {
                    assertTrue(recorder.stopped && recorder.closed && decoder.closed)
                    events.add("final:$text")
                }
                override fun error(failure: RecognitionFailure) { events.add(failure.name) }
            }
        )
        fun runWorker() { work.toList().also { work.clear() }.forEach { it.run() } }
        fun deliver() { callbacks.toList().also { callbacks.clear() }.forEach { it() } }
    }

    @Test fun completesAFullPhraseAfterReleasingTheMicrophone() {
        val f = Fixture()
        f.capture.start()
        assertTrue(f.events.isEmpty())
        f.runWorker()
        assertTrue(f.recorder.started && f.recorder.closed)
        f.deliver()
        assertEquals(listOf("ready", "partial:bonjour", "final:bonjour comment vas tu"), f.events)
    }

    @Test fun anEmptyRecorderReadDoesNotAdvertiseAMicrophoneReady() {
        val f = Fixture(Recorder(0))
        f.capture.start(); f.runWorker(); f.deliver()
        assertEquals(listOf(RecognitionFailure.TEMPORARY.name), f.events)
        assertTrue(f.recorder.closed)
    }

    @Test fun cancellingBeforeTheWorkerRunsNeverOpensTheMicrophone() {
        val f = Fixture()
        f.capture.start(); f.capture.close(); f.runWorker(); f.deliver()
        assertFalse(f.recorder.started)
        assertTrue(f.events.isEmpty())
    }

    @Test fun leavingTheScreenRejectsAlreadyQueuedResults() {
        val f = Fixture()
        f.capture.start(); f.runWorker(); f.capture.close(); f.deliver()
        assertTrue(f.events.isEmpty())
        assertTrue(f.recorder.closed)
    }

    @Test fun anUnavailableModelDoesNotOpenTheMicrophone() {
        val f = Fixture(decoderFails = true)
        f.capture.start(); f.runWorker(); f.deliver()
        assertFalse(f.recorder.started)
        assertEquals(listOf(RecognitionFailure.MODEL_UNAVAILABLE.name), f.events)
    }

    @Test fun modelExtractionRejectsParentPaths() {
        val bytes = ByteArrayOutputStream()
        ZipOutputStream(bytes).use { zip ->
            zip.putNextEntry(ZipEntry("model/../escape"))
            zip.write(1)
            zip.closeEntry()
        }
        val root = temporary.newFolder()
        assertThrows(IllegalArgumentException::class.java) {
            unpackFrenchModel(ByteArrayInputStream(bytes.toByteArray()), root, "model")
        }
        assertFalse(root.resolve("escape").exists())
    }
}
