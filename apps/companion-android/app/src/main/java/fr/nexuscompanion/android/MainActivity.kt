package fr.nexuscompanion.android

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Bundle
import android.graphics.Color
import android.graphics.Typeface
import android.view.Gravity
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.ScrollView

/** Native first-run shell: no WebView, browser, server, or network permission. */
class MainActivity : Activity() {
    private lateinit var controller: NativeVoiceController
    private lateinit var library: LocalKnowledgeLibrary
    private lateinit var face: TextView
    private lateinit var status: TextView
    private lateinit var answer: TextView
    private lateinit var voiceButton: Button
    private var voiceActive = false
    private var autoListenEnabled = true
    private var foreground = false
    private var permissionRequestInFlight = false
    private val voicePreferences by lazy { getSharedPreferences("nexus_voice_preferences", MODE_PRIVATE) }
    private val audioRequestCode = 410

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        library = LocalKnowledgeLibrary(applicationContext)
        autoListenEnabled = voicePreferences.getBoolean("auto_listen_enabled", true)
        fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(20), dp(24), dp(20), dp(20))
            setBackgroundColor(Color.rgb(10, 22, 43))
            setOnApplyWindowInsetsListener { view, insets ->
                val bars = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.ime())
                view.setPadding(bars.left + dp(20), bars.top + dp(16), bars.right + dp(20), bars.bottom + dp(20))
                insets
            }
        }
        fun label(text: String, size: Float): TextView = TextView(this).apply {
            this.text = text
            textSize = size
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
        }
        face = label(getString(R.string.face_idle), 68f).apply {
            setTextColor(Color.CYAN)
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, 40, 0, 40)
        }
        status = label(getString(R.string.offline_status), 17f)
        answer = label(getString(R.string.greeting), 19f)
        val input = EditText(this).apply {
            hint = getString(R.string.input_hint)
            setSingleLine(true)
            setTextColor(Color.WHITE)
            setHintTextColor(Color.LTGRAY)
        }
        val send = Button(this).apply { text = getString(R.string.send) }
        val remember = Button(this).apply { text = getString(R.string.remember) }
        val searchLibrary = Button(this).apply { text = getString(R.string.search_library) }
        voiceButton = Button(this).apply { text = getString(R.string.activate_microphone) }
        val layout = LinearLayout.LayoutParams(-1, -2)
        root.addView(face, layout)
        root.addView(status, layout)
        root.addView(answer, layout)
        root.addView(input, layout)
        root.addView(send, layout)
        root.addView(remember, layout)
        root.addView(searchLibrary, layout)
        root.addView(voiceButton, layout)
        setContentView(ScrollView(this).apply { addView(root) })
        window.insetsController?.setSystemBarsAppearance(0,
            WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS)
        root.requestApplyInsets()

        controller = NativeVoiceController(
            context = this,
            onStatus = { status.text = it },
            onCommand = { message -> respond(message) },
            onSpeechState = { audible ->
                face.text = if (audible) getString(R.string.face_speaking) else getString(R.string.face_idle)
            },
            onSessionState = { active ->
                voiceActive = active
                voiceButton.text = if (active) getString(R.string.deactivate_microphone) else getString(R.string.activate_microphone)
            }
        )
        remember.setOnClickListener {
            val note = input.text.toString().trim()
            if (note.isNotEmpty()) {
                try {
                    library.addUserNote(note.take(80), note)
                    answer.text = getString(R.string.note_saved)
                    input.setText("")
                } catch (_: IllegalArgumentException) {
                    answer.text = getString(R.string.note_invalid)
                } catch (_: IllegalStateException) {
                    answer.text = getString(R.string.library_full)
                } catch (_: Exception) {
                    answer.text = getString(R.string.library_write_failed)
                }
            }
        }
        searchLibrary.setOnClickListener {
            val query = input.text.toString().trim()
            try {
                val matches = library.search(query)
                answer.text = if (matches.isEmpty()) getString(R.string.library_no_results)
                    else matches.joinToString("\n\n") { getString(R.string.library_entry, it.title, it.excerpt, it.source) }
            } catch (_: Exception) {
                answer.text = getString(R.string.library_read_failed)
            }
        }
        send.setOnClickListener {
            val message = input.text.toString().trim()
            if (message.isNotEmpty()) {
                respond(message)
                input.setText("")
            }
        }
        voiceButton.setOnClickListener {
            if (voiceActive) {
                autoListenEnabled = false
                voicePreferences.edit().putBoolean("auto_listen_enabled", false).apply()
                controller.stop()
                voiceActive = false
                voiceButton.text = getString(R.string.activate_microphone)
            } else if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                autoListenEnabled = true
                voicePreferences.edit().putBoolean("auto_listen_enabled", true).apply()
                activateVoice()
            } else {
                requestMicrophonePermission()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        foreground = true
        if (!::controller.isInitialized || !autoListenEnabled || voiceActive || permissionRequestInFlight) return
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            activateVoice()
        } else if (!voicePreferences.getBoolean("microphone_permission_prompted", false)) {
            requestMicrophonePermission()
        } else {
            status.text = getString(R.string.microphone_permission_needed)
        }
    }

    private fun respond(message: String) {
        val reply = OfflineConversation.reply(message)
        answer.text = reply
        if (voiceActive) controller.speak(reply)
    }

    private fun activateVoice() {
        if (!foreground || checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) return
        if (!controller.available()) {
            status.text = getString(R.string.offline_recognition_unavailable)
            return
        }
        controller.start()
        // The controller reports its actual session state; do not assume startup succeeded.
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == audioRequestCode) {
            permissionRequestInFlight = false
            if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
                autoListenEnabled = true
                voicePreferences.edit().putBoolean("auto_listen_enabled", true).apply()
                if (foreground) activateVoice()
            }
            else status.text = getString(R.string.microphone_permission_denied)
        }
    }

    private fun requestMicrophonePermission() {
        if (permissionRequestInFlight) return
        permissionRequestInFlight = true
        voicePreferences.edit().putBoolean("microphone_permission_prompted", true).apply()
        requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), audioRequestCode)
    }

    override fun onPause() {
        foreground = false
        if (::controller.isInitialized) controller.stop()
        super.onPause()
    }

    override fun onStop() {
        super.onStop()
        foreground = false
        // No background surveillance: the microphone is off outside the foreground.
        if (::controller.isInitialized) controller.stop()
        voiceActive = false
        if (::voiceButton.isInitialized) voiceButton.text = getString(R.string.activate_microphone)
    }

    override fun onDestroy() {
        if (::controller.isInitialized) controller.stop()
        super.onDestroy()
    }
}
