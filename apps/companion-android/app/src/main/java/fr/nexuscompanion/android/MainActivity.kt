package fr.nexuscompanion.android

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputFilter
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.ProgressBar
import android.widget.TextView
import java.util.concurrent.Executors

/** Native UI. A single worker owns the packaged brain and private library operations. */
class MainActivity : Activity() {
    private lateinit var controller: NativeVoiceController
    private lateinit var library: LocalKnowledgeLibrary
    private lateinit var brain: NativeBrainService
    private lateinit var face: TextView
    private lateinit var status: TextView
    private lateinit var answer: TextView
    private lateinit var playbackNotice: TextView
    private lateinit var input: EditText
    private lateinit var voiceButton: Button
    private lateinit var directVoiceButton: Button
    private lateinit var transcript: TextView
    private lateinit var microphoneLevel: ProgressBar
    private lateinit var retainButton: Button
    private val taskButtons = mutableListOf<Button>()
    private val worker = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private var pendingCandidate: NativeBrainService.Candidate? = null
    private var voiceActive = false
    private var voiceStatus: String? = null
    private var autoListenEnabled = true
    private var foreground = false
    private var destroyed = false
    private var busy = false
    private var permissionRequestInFlight = false
    private var dialogDepth = 0
    private val dialogs = mutableSetOf<AlertDialog>()
    private var resumeVoiceAfterDialogs = false
    private val voicePreferences by lazy { getSharedPreferences("nexus_voice_preferences", MODE_PRIVATE) }
    private val audioRequestCode = 410

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        library = LocalKnowledgeLibrary(applicationContext)
        brain = NativeBrainService(applicationContext, library)
        autoListenEnabled = voicePreferences.getBoolean("auto_listen_enabled", true)
        buildScreen()
        controller = NativeVoiceController(
            context = this,
            onStatus = { message ->
                voiceStatus = message
                if (!busy) status.text = message
            },
            onCommand = ::respond,
            onSpeechState = { audible -> face.text = getString(if (audible) R.string.face_speaking else R.string.face_idle) },
            onSessionState = { active ->
                voiceActive = active
                voiceButton.setText(if (active) R.string.deactivate_microphone else R.string.activate_microphone)
                directVoiceButton.isEnabled = active && !busy
            },
            onTranscript = { heard, final ->
                transcript.text = if (heard.isEmpty()) "" else getString(
                    if (final) R.string.voice_heard else R.string.voice_hearing, heard
                )
                transcript.visibility = if (heard.isEmpty()) View.GONE else View.VISIBLE
            },
            onInputLevel = { microphoneLevel.progress = it },
            onSpeechOutput = { output ->
                when (output) {
                    SpeechOutputState.UNAVAILABLE, SpeechOutputState.FAILED, SpeechOutputState.INTERRUPTED -> {
                        playbackNotice.setText(output.messageResource())
                        playbackNotice.visibility = View.VISIBLE
                    }
                    else -> playbackNotice.visibility = View.GONE
                }
            }
        )
    }

    private fun buildScreen() {
        fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setBackgroundColor(Color.rgb(10, 22, 43))
            setPadding(dp(20), dp(24), dp(20), dp(20))
            setOnApplyWindowInsetsListener { view, insets ->
                val bars = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.ime())
                view.setPadding(bars.left + dp(20), bars.top + dp(16), bars.right + dp(20), bars.bottom + dp(20))
                insets
            }
        }
        val layout = LinearLayout.LayoutParams(-1, -2)
        fun label(resource: Int, size: Float): TextView = TextView(this).apply {
            setText(resource); textSize = size; setTextColor(Color.WHITE); gravity = Gravity.CENTER
            root.addView(this, layout)
        }
        fun button(resource: Int, action: () -> Unit): Button = Button(this).apply {
            setText(resource); setOnClickListener { action() }; root.addView(this, layout)
            taskButtons.add(this)
        }
        face = label(R.string.face_idle, 60f).apply {
            contentDescription = getString(R.string.app_name)
            setTextColor(Color.CYAN); typeface = Typeface.DEFAULT_BOLD; setPadding(0, dp(12), 0, dp(12))
        }
        if (BuildConfig.IS_PREVIEW) label(R.string.preview_warning, 15f)
        status = label(R.string.offline_status, 16f)
        voiceButton = button(R.string.activate_microphone) { toggleVoice() }
        taskButtons.remove(voiceButton) // Keep Stop visible above long replies and available during work.
        directVoiceButton = button(R.string.speak_now) {
            if (foreground && voiceActive && !busy) controller.listenForCommand()
        }.apply { isEnabled = false }
        taskButtons.remove(directVoiceButton)
        microphoneLevel = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100; contentDescription = getString(R.string.microphone_level)
            root.addView(this, LinearLayout.LayoutParams(-1, dp(8)))
        }
        button(R.string.voice_settings) { startActivity(android.content.Intent(this, VoiceSettingsActivity::class.java)) }
        transcript = label(R.string.voice_transcript_empty, 16f).apply {
            setTextColor(Color.LTGRAY); maxLines = 4; ellipsize = TextUtils.TruncateAt.END
            visibility = View.GONE
        }
        answer = label(R.string.greeting, 18f)
        playbackNotice = label(R.string.voice_output_idle, 15f).apply { visibility = View.GONE }
        input = EditText(this).apply {
            setHint(R.string.input_hint); setSingleLine(true); setTextColor(Color.WHITE)
            setHintTextColor(Color.LTGRAY); filters = arrayOf(InputFilter.LengthFilter(2000))
            root.addView(this, layout)
        }
        button(R.string.send) {
            val message = input.text.toString().trim()
            if (message.isNotEmpty() && !busy) {
                input.setText("")
                getSystemService(InputMethodManager::class.java).hideSoftInputFromWindow(input.windowToken, 0)
                input.clearFocus()
                respond(message)
            }
        }
        button(R.string.remember) {
            val content = input.text.toString().trim()
            if (content.isNotEmpty()) confirmNote(content)
        }
        retainButton = button(R.string.retain_proposal) { pendingCandidate?.let { confirmNote(it.content, it.sensitive) } }
            .apply { visibility = View.GONE }
        button(R.string.my_notes) { showNotes() }
        button(R.string.updates) { startActivity(android.content.Intent(this, UpdateActivity::class.java)) }
        button(R.string.about) {
            runLocal(R.string.working, R.string.operation_failed, {
                assets.open("THIRD_PARTY_NOTICES.txt").bufferedReader().use { it.readText() }
            }) { notices ->
                if (foreground) showDialog(AlertDialog.Builder(this).setTitle(R.string.about)
                    .setMessage(getString(R.string.about_text, BuildConfig.VERSION_NAME) + "\n\n" + notices)
                    .setPositiveButton(R.string.close, null).create())
            }
        }
        setContentView(ScrollView(this).apply {
            setBackgroundColor(Color.rgb(10, 22, 43)); isFillViewport = true; addView(root)
        })
        window.insetsController?.setSystemBarsAppearance(0,
            WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS)
        root.requestApplyInsets()
    }

    private fun setBusy(active: Boolean) {
        busy = active
        taskButtons.forEach { it.isEnabled = !active }
        input.isEnabled = !active
        directVoiceButton.isEnabled = voiceActive && !active
    }

    private fun restoreStatus() {
        val message = voiceStatus
        if (voiceActive && message != null) status.text = message else status.setText(R.string.local_ready)
    }

    private fun <T> runLocal(
        progress: Int,
        failureMessage: Int,
        task: () -> T,
        voiceReply: (T) -> String? = { null },
        success: (T) -> Unit
    ) {
        if (busy || destroyed) return
        setBusy(true)
        controller.beginResponse()
        status.setText(progress)
        worker.execute {
            try {
                val result = task()
                main.post {
                    if (!destroyed) {
                        setBusy(false)
                        restoreStatus()
                        success(result)
                        finishLocalVoice(voiceReply(result))
                    }
                }
            } catch (failure: Exception) {
                main.post {
                    if (!destroyed) {
                        setBusy(false)
                        answer.setText(when (failure) {
                            is InvalidKnowledgeNote -> R.string.note_invalid
                            is KnowledgeCapacityReached -> R.string.library_full
                            else -> failureMessage
                        })
                        restoreStatus()
                        finishLocalVoice(null)
                    }
                }
            }
        }
    }

    private fun finishLocalVoice(text: String?) {
        if (!foreground || dialogDepth > 0) return
        if (voiceActive) controller.finishResponse(text)
        else if (text != null && autoListenEnabled &&
            checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            // Keep the existing voice opt-in when the recognition service cannot run.
            controller.playReply(text)
        }
    }

    private fun respond(message: String) {
        if (busy || message.isBlank() || message.length > 2000) return
        pendingCandidate = null
        retainButton.visibility = View.GONE
        runLocal(R.string.thinking, R.string.brain_failed, { brain.reply(message) }, voiceReply = { it.text }) { reply ->
            answer.text = if (reply.notesAvailable) reply.text
                else getString(R.string.reply_with_library_warning, reply.text, getString(R.string.library_read_failed))
            pendingCandidate = if (reply.notesAvailable) reply.candidate else null
            retainButton.visibility = if (pendingCandidate != null) View.VISIBLE else View.GONE
        }
    }

    private fun confirmNote(content: String, sensitive: Boolean = false) {
        showDialog(AlertDialog.Builder(this).setTitle(R.string.confirm_note_title)
            .setMessage(getString(if (sensitive) R.string.confirm_sensitive_note_message else R.string.confirm_note_message, content))
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.save_note) { _, _ ->
                runLocal(R.string.working, R.string.library_write_failed, {
                    library.addUserNote(content.take(80), content)
                }) {
                    answer.setText(R.string.note_saved)
                    if (input.text.toString().trim() == content) input.setText("")
                    if (pendingCandidate?.content == content) {
                        pendingCandidate = null; retainButton.visibility = View.GONE
                    }
                }
            }.create())
    }

    private fun showNotes() {
        val query = input.text.toString().trim()
        runLocal(R.string.working, R.string.library_read_failed, {
            if (query.isBlank()) library.list().sortedByDescending { it.recordedAt }.take(50) else library.search(query)
        }) { notes ->
            if (notes.isEmpty()) { answer.setText(R.string.library_no_results); return@runLocal }
            if (foreground) showDialog(AlertDialog.Builder(this).setTitle(R.string.my_notes)
                .setItems(notes.map { it.title }.toTypedArray()) { _, position -> showNote(notes[position]) }
                .setNegativeButton(R.string.close, null).create())
        }
    }

    private fun showNote(note: LocalKnowledgeLibrary.Entry) {
        showDialog(AlertDialog.Builder(this).setTitle(note.title)
            .setMessage(getString(R.string.library_entry, note.title, note.excerpt, note.source))
            .setNegativeButton(R.string.close, null)
            .setNeutralButton(R.string.delete_note) { _, _ ->
                showDialog(AlertDialog.Builder(this).setTitle(R.string.delete_note)
                    .setMessage(R.string.delete_note_confirmation).setNegativeButton(R.string.cancel, null)
                    .setPositiveButton(R.string.delete_note) { _, _ ->
                        runLocal(R.string.working, R.string.library_write_failed, { library.remove(note.id) }) { removed ->
                            answer.setText(if (removed) R.string.note_deleted else R.string.library_no_results)
                        }
                    }.create())
            }.create())
    }

    /** Notes can contain private text: keep the microphone off while any dialog is visible. */
    private fun showDialog(dialog: AlertDialog) {
        if (dialogDepth == 0) {
            resumeVoiceAfterDialogs = voiceActive
            controller.stop()
        }
        dialogDepth++
        dialogs.add(dialog)
        dialog.setOnDismissListener {
            dialogs.remove(dialog)
            dialogDepth--
            if (dialogDepth == 0 && resumeVoiceAfterDialogs) {
                resumeVoiceAfterDialogs = false
                if (foreground && autoListenEnabled) activateVoice()
            }
        }
        dialog.show()
    }

    private fun toggleVoice() {
        if (voiceActive) {
            autoListenEnabled = false
            voicePreferences.edit().putBoolean("auto_listen_enabled", false).apply()
            controller.stop()
        } else if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            autoListenEnabled = true
            voicePreferences.edit().putBoolean("auto_listen_enabled", true).apply()
            activateVoice()
        } else requestMicrophonePermission()
    }

    override fun onResume() {
        super.onResume()
        foreground = true
        if (!autoListenEnabled || voiceActive || permissionRequestInFlight || dialogDepth > 0) return
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) activateVoice()
        else if (!voicePreferences.getBoolean("microphone_permission_prompted", false)) requestMicrophonePermission()
        else status.setText(R.string.microphone_permission_needed)
    }

    private fun activateVoice() {
        if (!foreground || dialogDepth > 0 || checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) return
        if (!controller.available()) { status.setText(R.string.offline_recognition_unavailable); return }
        controller.start()
        if (busy && voiceActive) controller.beginResponse()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == audioRequestCode) {
            permissionRequestInFlight = false
            if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
                autoListenEnabled = true
                voicePreferences.edit().putBoolean("auto_listen_enabled", true).apply()
                if (foreground) activateVoice()
            } else status.setText(R.string.microphone_permission_denied)
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

    override fun onDestroy() {
        destroyed = true
        foreground = false
        dialogs.toList().forEach { it.dismiss() }
        dialogs.clear()
        if (::controller.isInitialized) controller.stop()
        worker.execute { brain.close() }
        worker.shutdown()
        super.onDestroy()
    }
}
