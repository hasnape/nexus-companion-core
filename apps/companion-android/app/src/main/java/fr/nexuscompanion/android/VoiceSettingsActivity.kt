package fr.nexuscompanion.android

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.graphics.Color
import android.media.AudioManager
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/** The main screen is paused while this screen is open: no listening or transcript collection. */
class VoiceSettingsActivity : Activity() {
    private var checker: FrenchVoiceSupport? = null
    private lateinit var playback: NativeVoiceController
    private lateinit var status: TextView
    private var foreground = false

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        volumeControlStream = AudioManager.STREAM_MUSIC
        fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(20), dp(24), dp(20), dp(20))
            setOnApplyWindowInsetsListener { view, insets ->
                val bars = insets.getInsets(WindowInsets.Type.systemBars())
                view.setPadding(bars.left + dp(20), bars.top + dp(20), bars.right + dp(20), bars.bottom + dp(20)); insets
            }
        }
        fun label(text: String, size: Float) = TextView(this).apply {
            this.text = text; textSize = size; setTextColor(Color.WHITE); gravity = Gravity.CENTER
            setPadding(0, dp(12), 0, dp(12)); root.addView(this, LinearLayout.LayoutParams(-1, -2))
        }
        fun button(text: Int, action: () -> Unit) = Button(this).apply {
            setText(text); setOnClickListener { action() }; root.addView(this, LinearLayout.LayoutParams(-1, -2))
        }
        label(getString(R.string.voice_settings), 24f)
        if (EmbeddedFrenchInput.isPackaged(this)) {
            status = label(getString(R.string.voice_model_embedded), 17f)
        } else {
            label(getString(R.string.voice_setup_explanation), 17f)
            status = label(getString(R.string.voice_model_checking), 17f)
            button(R.string.voice_check_model) { checker?.check() }
            val download = button(R.string.voice_download_model) { checker?.requestDownload() }.apply { isEnabled = false }
            button(R.string.voice_android_settings) {
                try { startActivity(Intent(Settings.ACTION_VOICE_INPUT_SETTINGS)) }
                catch (_: ActivityNotFoundException) { status.setText(R.string.voice_settings_unavailable) }
            }
            checker = FrenchVoiceSupport(this) { model ->
                download.isEnabled = model == FrenchModelState.DOWNLOADABLE
                status.setText(when (model) {
                    FrenchModelState.CHECKING -> R.string.voice_model_checking
                    FrenchModelState.READY -> R.string.voice_model_ready
                    FrenchModelState.DOWNLOADABLE -> R.string.voice_model_missing
                    FrenchModelState.PENDING -> R.string.voice_model_pending
                    FrenchModelState.REQUESTED -> R.string.voice_model_requested
                    FrenchModelState.UNAVAILABLE -> R.string.voice_model_unavailable
                    FrenchModelState.UNKNOWN -> R.string.voice_model_unknown
                    FrenchModelState.SERVICE_ABSENT -> R.string.offline_recognition_unavailable
                    FrenchModelState.PERMISSION -> R.string.microphone_permission_needed
                })
            }
        }
        val outputStatus = label(getString(R.string.voice_output_idle), 17f)
        val outputTest = button(R.string.voice_output_test) {
            if (foreground) playback.playReply(getString(R.string.voice_output_sample))
        }
        playback = NativeVoiceController(this, onStatus = {}, onCommand = {}, onSpeechState = {}, onSpeechOutput = { output ->
            outputStatus.setText(output.messageResource())
            outputTest.isEnabled = output != SpeechOutputState.PREPARING && output != SpeechOutputState.PLAYING
        })
        val diagnostic = InstallationDiagnostic.read(this)
        label(getString(R.string.voice_installation_summary, diagnostic.originalVersion ?: getString(R.string.voice_original_absent),
            getString(when (diagnostic.signaturesMatch) {
                true -> R.string.voice_signature_same
                false -> R.string.voice_signature_different
                null -> R.string.voice_signature_unknown
            })), 16f)
        button(R.string.voice_copy_diagnostic) {
            getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("Nexus diagnostic", diagnostic.details))
            status.setText(R.string.voice_diagnostic_copied)
        }
        button(R.string.close) { finish() }
        setContentView(ScrollView(this).apply {
            isFillViewport = true; setBackgroundColor(Color.rgb(10, 22, 43)); addView(root)
        })
        window.insetsController?.setSystemBarsAppearance(0,
            WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS)
        root.requestApplyInsets()
    }

    override fun onResume() { super.onResume(); foreground = true; checker?.check() }
    override fun onPause() { foreground = false; checker?.close(); playback.stop(); super.onPause() }
    override fun onDestroy() { checker?.close(); playback.close(); super.onDestroy() }
}
