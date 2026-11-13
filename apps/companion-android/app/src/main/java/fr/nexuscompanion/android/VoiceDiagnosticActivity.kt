package fr.nexuscompanion.android

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import org.json.JSONObject

internal class VoiceDiagnosticReport {
    var first: String? = null
    var second: String? = null
    var playbackStarted = false
    var playbackCompleted = false
    var microphoneDuringPlayback = false
    var resumed = false
    var interrupted = false
    fun json(): String = JSONObject().put("schema", 1).put("version", BuildConfig.VERSION_NAME)
        .put("timestamp", System.currentTimeMillis()).put("firstCapture", first ?: JSONObject.NULL)
        .put("secondCapture", second ?: JSONObject.NULL).put("playbackStarted", playbackStarted)
        .put("playbackCompleted", playbackCompleted).put("microphoneDuringPlayback", microphoneDuringPlayback)
        .put("resumed", resumed).put("interrupted", interrupted)
        .put("completed", !first.isNullOrBlank() && !second.isNullOrBlank() && playbackStarted && playbackCompleted && resumed && !microphoneDuringPlayback && !interrupted)
        .put("scope", "Callbacks Android observés ; la qualité acoustique et l’audibilité sont à apprécier par la personne.")
        .toString(2)
}

/** Runs on the phone. It never claims that simulated callbacks constitute an acoustic measurement. */
class VoiceDiagnosticActivity : Activity() {
    private val main = Handler(Looper.getMainLooper())
    private var controller: NativeVoiceController? = null
    private var report = VoiceDiagnosticReport()
    private lateinit var status: TextView
    private var phase = 0
    private var microphoneReady = false
    private var playing = false
    private val timeout = Runnable {
        if (phase in 1..3) { report.interrupted = true; stopTest(); status.text = getString(R.string.vocal_ui_1, report.json()) }
    }
    override fun onCreate(state: Bundle?) {
        super.onCreate(state); title = "Test vocal sur ce téléphone"
        val panel = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(28, 28, 28, 28) }
        setContentView(android.widget.ScrollView(this).apply { addView(panel) })
        status = TextView(this).apply { textSize = 17f; text = getString(R.string.vocal_ui_2) }.also(panel::addView)
        Button(this).apply { text = getString(R.string.vocal_ui_3); setOnClickListener {
            if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) startTest()
            else requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 452)
        }; panel.addView(this) }
        Button(this).apply { text = getString(R.string.vocal_ui_4); setOnClickListener {
            if (phase in 1..3) report.interrupted = true
            stopTest(); status.text = getString(R.string.vocal_ui_5, report.json())
        }; panel.addView(this) }
        Button(this).apply { text = getString(R.string.vocal_ui_6); setOnClickListener {
            if (phase in 1..3) { status.text = getString(R.string.vocal_ui_7); return@setOnClickListener }
            startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE)
                .setType("application/json").putExtra(Intent.EXTRA_TITLE, "nexus-test-vocal.json"), 453)
        }; panel.addView(this) }
    }
    private fun armTimeout() { main.removeCallbacks(timeout); main.postDelayed(timeout, 45_000) }
    private fun startTest() {
        stopTest(); report = VoiceDiagnosticReport(); phase = 1
        controller = NativeVoiceController(this,
            onStatus = { if (phase in 1..3) status.text = getString(R.string.vocal_ui_status, instruction(), it) },
            onCommand = { text ->
                if (phase == 1) {
                    report.first = text.take(600); phase = 2; armTimeout()
                    controller?.beginResponse()
                    controller?.finishResponse("Première capture reçue. Après ma réponse, dis : le microphone fonctionne.", true)
                } else if (phase == 3) {
                    report.second = text.take(600); phase = 4; stopTest()
                    status.text = getString(R.string.vocal_ui_8, report.json())
                }
            },
            onSpeechState = { playing = it; if (it) { report.playbackStarted = true; if (microphoneReady) report.microphoneDuringPlayback = true } },
            onSpeechOutput = { output ->
                if (phase == 2 && output == SpeechOutputState.COMPLETED) report.playbackCompleted = true
                if (phase == 2 && output in setOf(SpeechOutputState.FAILED, SpeechOutputState.UNAVAILABLE, SpeechOutputState.INTERRUPTED)) {
                    report.interrupted = true; main.post { stopTest(); status.text = getString(R.string.vocal_ui_9, report.json()) }
                }
            },
            onMicrophoneReady = { ready ->
                microphoneReady = ready
                if (ready && playing) report.microphoneDuringPlayback = true
                if (ready && phase == 2 && report.playbackCompleted) { report.resumed = true; phase = 3; status.text = instruction(); armTimeout() }
            })
        status.text = instruction(); controller?.start(); controller?.listenForCommand(); armTimeout()
    }
    private fun instruction() = if (phase == 1) "Dis : bonjour Nexus." else if (phase == 3) "Dis maintenant : le microphone fonctionne." else "Écoute la réponse de Nexus ; le micro doit être en pause."
    private fun stopTest() {
        if (phase in 1..3) phase = 0
        main.removeCallbacks(timeout); controller?.close(); controller = null
        microphoneReady = false; playing = false
    }
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 452 && grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) startTest()
        else status.text = getString(R.string.vocal_ui_10)
    }
    @Deprecated("Android activity result")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 453 && resultCode == RESULT_OK) try {
            data?.data?.let { uri -> contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(report.json()) } }
            status.text = getString(R.string.vocal_ui_11)
        } catch (_: Exception) { status.text = getString(R.string.vocal_ui_12) }
    }
    override fun onPause() { if (phase in 1..3) { report.interrupted = true; stopTest(); status.text = getString(R.string.vocal_ui_13, report.json()) }; super.onPause() }
    override fun onDestroy() { stopTest(); super.onDestroy() }
}
