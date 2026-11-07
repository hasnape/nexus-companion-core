package fr.nexuscompanion.android

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Handler
import android.os.Looper
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.concurrent.Executors
import java.util.zip.ZipInputStream

/** The packaged model and decoder stay local. Only the worker owns native decoder objects. */
internal class EmbeddedFrenchInput(context: Context) : VoiceInput {
    private val app = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private val worker = lazy { Executors.newSingleThreadExecutor { Thread(it, "nexus-local-recognition") } }
    private var model: Model? = null

    override fun available() = isPackaged(app)

    override fun recognizer(events: RecognitionEvents) = EmbeddedSpeechCapture(
        worker.value,
        dispatch = { action -> main.post { action() } },
        openRecorder = { AndroidPcmRecorder(app) },
        openDecoder = ::openDecoder,
        events = events
    )

    private fun openDecoder(): LocalSpeechDecoder {
        val loaded = model ?: Model(installModel(app).absolutePath).also { model = it }
        val decoder = Recognizer(loaded, EmbeddedSpeechCapture.SAMPLE_RATE.toFloat())
        return object : LocalSpeechDecoder {
            override fun accept(samples: ShortArray, count: Int) = decoder.acceptWaveForm(samples, count)
            override fun partial() = JSONObject(decoder.partialResult).optString("partial")
            override fun result() = JSONObject(decoder.result).optString("text")
            override fun finish() = JSONObject(decoder.finalResult).optString("text")
            override fun close() = decoder.close()
        }
    }

    override fun close() {
        if (worker.isInitialized() && !worker.value.isShutdown) {
            worker.value.execute {
                model?.close()
                model = null
            }
            worker.value.shutdown()
        }
    }

    companion object {
        private val assetPath get() = "speech/${BuildConfig.FRENCH_MODEL_ID}.zip"

        fun isPackaged(context: Context): Boolean = try {
            context.assets.open(assetPath).use { true }
        } catch (_: IOException) { false }

        @Synchronized
        private fun installModel(context: Context): File {
            val models = File(context.noBackupFilesDir, "speech-models").apply { mkdirs() }
            val target = File(models, BuildConfig.FRENCH_MODEL_CACHE_ID)
            if (target.isDirectory) return target
            val staging = Files.createTempDirectory(models.toPath(), "extract-").toFile()
            try {
                val unpacked = unpackFrenchModel(context.assets.open(assetPath), staging, BuildConfig.FRENCH_MODEL_ID)
                Files.move(unpacked.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE)
            } finally { staging.deleteRecursively() }
            return target
        }
    }
}

/** The archive is checksum-verified at build time. Extraction stays within the private staging directory. */
internal fun unpackFrenchModel(source: java.io.InputStream, staging: File, modelId: String): File {
    val base = staging.canonicalFile
    val prefix = "$modelId/"
    var written = 0L
    ZipInputStream(source.buffered()).use { zip ->
        val buffer = ByteArray(65536)
        while (true) {
            val entry = zip.nextEntry ?: break
            require(entry.name.startsWith(prefix) && ".." !in entry.name.split('/')) { "Invalid French model archive root" }
            val destination = File(base, entry.name).canonicalFile
            require(destination.toPath().startsWith(base.toPath()) && destination != base) { "Invalid model archive path" }
            if (entry.isDirectory) destination.mkdirs()
            else {
                destination.parentFile?.mkdirs()
                destination.outputStream().buffered().use { output ->
                    while (true) {
                        val count = zip.read(buffer)
                        if (count < 0) break
                        written += count
                        require(written <= 128L * 1024L * 1024L) { "French model archive exceeds the size limit" }
                        output.write(buffer, 0, count)
                    }
                }
            }
            zip.closeEntry()
        }
    }
    val extracted = File(base, modelId)
    require(listOf("am/final.mdl", "conf/model.conf", "graph/HCLr.fst", "graph/Gr.fst")
        .all { File(extracted, it).let { file -> file.isFile && file.length() > 0 } }) { "Incomplete French model archive" }
    return extracted
}

private class AndroidPcmRecorder(context: Context) : PcmRecorder {
    private val audio: AudioRecord

    init {
        if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            throw SecurityException("Microphone permission is missing")
        }
        val rate = EmbeddedSpeechCapture.SAMPLE_RATE
        val minimum = AudioRecord.getMinBufferSize(rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        if (minimum <= 0) throw IOException("Microphone PCM format is unavailable")
        audio = AudioRecord.Builder()
            .setAudioSource(MediaRecorder.AudioSource.VOICE_RECOGNITION)
            .setAudioFormat(AudioFormat.Builder().setSampleRate(rate)
                .setChannelMask(AudioFormat.CHANNEL_IN_MONO).setEncoding(AudioFormat.ENCODING_PCM_16BIT).build())
            .setBufferSizeInBytes(maxOf(minimum, EmbeddedSpeechCapture.FRAME_SAMPLES * 4))
            .build()
        if (audio.state != AudioRecord.STATE_INITIALIZED) {
            audio.release()
            throw IOException("Microphone was not initialized")
        }
    }

    @SuppressLint("MissingPermission") // The permission is checked before creating this recorder.
    override fun start() {
        audio.startRecording()
        if (audio.recordingState != AudioRecord.RECORDSTATE_RECORDING) throw IOException("Microphone did not start")
    }

    override fun read(samples: ShortArray) = audio.read(samples, 0, samples.size, AudioRecord.READ_BLOCKING)
    override fun stop() {
        if (audio.recordingState == AudioRecord.RECORDSTATE_RECORDING) audio.stop()
    }
    override fun close() = audio.release()
}
