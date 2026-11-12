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
    @Test fun anIncompleteSearchCanContinueAfterAnEndpoint() {
        val recorder=Recorder();var frames=0;val heard=mutableListOf<String>()
        val decoder=object:LocalSpeechDecoder {
            override fun accept(samples:ShortArray,count:Int):Boolean { frames++;return frames==1 || frames==5 }
            override fun partial()=if(frames>1) "les volcans" else ""
            override fun result()=if(frames==1) "Nexus cherche sur Internet" else "les volcans"
            override fun finish()=""
            override fun close()=Unit
        }
        EmbeddedSpeechCapture(Executor { it.run() },{it()},{recorder},{decoder},object:RecognitionEvents {
            override fun ready()=Unit
            override fun result(text:String) { assertTrue(recorder.closed);heard.add(text) }
            override fun error(failure:RecognitionFailure) { fail(failure.name) }
        }).start()
        assertEquals(listOf("Nexus cherche sur Internet les volcans"),heard)
        assertEquals(5,frames)
    }
    @Test fun anIncompletePrefixWaitsOnlyBrieflyWhenNoContinuationArrives() {
        val recorder=Recorder();var frames=0;val heard=mutableListOf<String>()
        val decoder=object:LocalSpeechDecoder {
            override fun accept(samples:ShortArray,count:Int):Boolean { frames++;return frames==1 }
            override fun partial()=""
            override fun result()="Nexus cherche sur Internet"
            override fun finish()=""
            override fun close()=Unit
        }
        EmbeddedSpeechCapture(Executor { it.run() },{it()},{recorder},{decoder},object:RecognitionEvents {
            override fun ready()=Unit
            override fun result(text:String) { heard.add(text) }
            override fun error(failure:RecognitionFailure) { fail(failure.name) }
        }).start()
        assertEquals(listOf("Nexus cherche sur Internet"),heard)
        assertTrue(frames in 2..20)
    }

    @Test fun anUnusableContinuationDoesNotSearchAnEarlierTopic() {
        val recorder = Recorder()
        var frames = 0
        val failures = mutableListOf<RecognitionFailure>()
        val decoder = object : LocalSpeechDecoder {
            override fun accept(samples: ShortArray, count: Int): Boolean {
                frames++
                return frames == 1 || frames == 3
            }
            override fun partial() = "mots incertains"
            override fun result() = if (frames == 1) "Nexus cherche sur Internet" else ""
            override fun finish() = ""
            override fun close() = Unit
        }
        EmbeddedSpeechCapture(Executor { it.run() }, { it() }, { recorder }, { decoder }, object : RecognitionEvents {
            override fun ready() = Unit
            override fun result(text: String) { fail("Unusable speech must not reuse a topic: $text") }
            override fun error(failure: RecognitionFailure) {
                assertTrue(recorder.closed)
                failures.add(failure)
            }
        }).start()
        assertEquals(listOf(RecognitionFailure.SILENCE), failures)
    }

    @Test fun cancellingAHeldSearchReleasesTheMicrophoneWithoutSendingACommand() {
        val recorder = Recorder()
        var frames = 0
        lateinit var capture: EmbeddedSpeechCapture
        val decoder = object : LocalSpeechDecoder {
            override fun accept(samples: ShortArray, count: Int): Boolean {
                frames++
                if (frames == 2) capture.close()
                return frames == 1
            }
            override fun partial() = ""
            override fun result() = "Nexus cherche sur Internet"
            override fun finish() = ""
            override fun close() = Unit
        }
        capture = EmbeddedSpeechCapture(Executor { it.run() }, { it() }, { recorder }, { decoder }, object : RecognitionEvents {
            override fun ready() = Unit
            override fun result(text: String) { fail("Cancelled speech must not send a command") }
            override fun error(failure: RecognitionFailure) { fail(failure.name) }
        })
        capture.start()
        assertTrue(recorder.stopped && recorder.closed)
        assertEquals(2, frames)
    }
    @Test fun aHeldPrefixNeverTurnsTheNextSegmentIntoAnApprovalAndNewWakeCommandsRemainSeparate() {
        for((next,expected) in listOf("oui" to "Nexus cherche sur Internet", "Nexus bonjour" to "Nexus bonjour", "stop" to "stop")) {
            val recorder=Recorder();var frames=0;val heard=mutableListOf<String>()
            val decoder=object:LocalSpeechDecoder {
                override fun accept(samples:ShortArray,count:Int):Boolean { frames++;return true }
                override fun partial()=""
                override fun result()=if(frames==1) "Nexus cherche sur Internet" else next
                override fun finish()=""
                override fun close()=Unit
            }
            EmbeddedSpeechCapture(Executor { it.run() },{it()},{recorder},{decoder},object:RecognitionEvents {
                override fun ready()=Unit
                override fun result(text:String) { heard.add(text) }
                override fun error(failure:RecognitionFailure) { fail(failure.name) }
            }).start()
            assertEquals(next,listOf(expected),heard)
        }
    }
    @Test fun sustainedDictationCanContinueBeyondOneMinuteAndFinalizesBeforeTheWatchdog() {
        val recorder = Recorder(); var samples = 0L
        val decoder = object : LocalSpeechDecoder {
            override fun accept(input: ShortArray, count: Int): Boolean { samples += count; return false }
            override fun partial() = "nexus retiens que cette observation continue"
            override fun result() = ""
            override fun finish() = "nexus retiens que voici la fin exacte"
            override fun close() = Unit
        }
        val heard = mutableListOf<String>()
        EmbeddedSpeechCapture(Executor { it.run() }, { it() }, { recorder }, { decoder }, object : RecognitionEvents {
            override fun ready() = Unit
            override fun result(text: String) { heard.add(text) }
            override fun error(failure: RecognitionFailure) { fail(failure.name) }
        }).start()
        assertTrue(samples > EmbeddedSpeechCapture.SAMPLE_RATE * 60L)
        assertTrue(samples < EmbeddedSpeechCapture.SAMPLE_RATE * 180L)
        assertEquals(listOf("nexus retiens que voici la fin exacte"), heard)
        assertTrue(recorder.closed)
    }
    @Test fun finalizesAQuietUtteranceEvenWhenNoPartialWasReturned() {
        val recorder = Recorder()
        var finalized = false
        val heard = mutableListOf<String>()
        val decoder = object : LocalSpeechDecoder {
            override fun accept(samples: ShortArray, count: Int) = false
            override fun partial() = ""
            override fun result() = ""
            override fun finish(): String { finalized = true; return "Nexus bonjour" }
            override fun close() = Unit
        }
        EmbeddedSpeechCapture(Executor { it.run() }, { it() }, { recorder }, { decoder }, object : RecognitionEvents {
            override fun ready() = Unit
            override fun result(text: String) { assertTrue(recorder.closed); heard.add(text) }
            override fun error(failure: RecognitionFailure) { fail(failure.name) }
        }).start()
        assertTrue(finalized)
        assertEquals(listOf("Nexus bonjour"), heard)
    }
    @get:Rule val temporary = TemporaryFolder()

    private class Recorder(private val count: Int = EmbeddedSpeechCapture.FRAME_SAMPLES) : PcmRecorder {
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
