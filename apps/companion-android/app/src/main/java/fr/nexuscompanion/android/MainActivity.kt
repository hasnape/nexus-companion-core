package fr.nexuscompanion.android

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.window.OnBackInvokedCallback
import android.window.OnBackInvokedDispatcher
import java.util.concurrent.Executors

/** Native UI. A single worker owns the packaged brain and private library operations. */
open class MainActivity : Activity() {
    private companion object {
        const val SPOKEN_REPLIES_KEY = "spoken_replies_enabled"
        const val FACE_MODE_STATE = "face_mode"
    }

    private lateinit var controller: NativeVoiceController
    private lateinit var library: LocalKnowledgeLibrary
    private lateinit var brain: NativeBrainService
    private lateinit var avatar: NexusAvatarView
    private lateinit var presenceLabel: TextView
    private lateinit var homeScroll: ScrollView
    private lateinit var faceScreen: View
    private lateinit var faceAvatar: NexusAvatarView
    private lateinit var facePresenceLabel: TextView
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
    private var microphoneReady = false
    private var speaking = false
    private var voiceStatus: String? = null
    private var autoListenEnabled = true
    private var foreground = false
    private var destroyed = false
    private var busy = false
    private var faceMode = false
    private var unregisterFaceBack: (() -> Unit)? = null
    private var permissionRequestInFlight = false
    private var dialogDepth = 0
    private val dialogs = mutableSetOf<AlertDialog>()
    private var resumeVoiceAfterDialogs = false
    private val voicePreferences by lazy { getSharedPreferences("nexus_voice_preferences", MODE_PRIVATE) }
    private val audioRequestCode = 410

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        volumeControlStream = AudioManager.STREAM_MUSIC
        library = LocalKnowledgeLibrary(applicationContext)
        brain = NativeBrainService(applicationContext, library)
        autoListenEnabled = voicePreferences.getBoolean("auto_listen_enabled", true)
        val spokenRepliesEnabled = voicePreferences.getBoolean(SPOKEN_REPLIES_KEY, autoListenEnabled)
        if (!voicePreferences.contains(SPOKEN_REPLIES_KEY)) {
            voicePreferences.edit().putBoolean(SPOKEN_REPLIES_KEY, spokenRepliesEnabled).apply()
        }
        buildScreen(spokenRepliesEnabled)
        controller = NativeVoiceController(
            context = this,
            onStatus = { message ->
                voiceStatus = message
                if (!busy) status.text = message
            },
            onCommand = ::respond,
            onSpeechState = { audible ->
                speaking = audible
                renderPresence()
            },
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
            },
            input = createVoiceInput(),
            onMicrophoneReady = { ready ->
                microphoneReady = ready
                renderPresence()
            }
        ).also { it.setSpokenRepliesEnabled(spokenRepliesEnabled) }
        if (savedInstanceState?.getBoolean(FACE_MODE_STATE) == true) setFaceMode(true)
    }

    internal open fun createVoiceInput(): VoiceInput = defaultVoiceInput(applicationContext)

    private fun buildScreen(spokenRepliesEnabled: Boolean) {
        setContentView(R.layout.activity_main)
        homeScroll = findViewById(R.id.home_scroll)
        faceScreen = layoutInflater.inflate(R.layout.face_mode, findViewById(android.R.id.content), false)
        addContentView(faceScreen, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT
        ))
        for (screen in listOf(homeScroll, faceScreen)) {
            screen.setOnApplyWindowInsetsListener { view, insets ->
                val bars = insets.getInsets(
                    WindowInsets.Type.systemBars() or WindowInsets.Type.ime() or WindowInsets.Type.displayCutout()
                )
                view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
                insets
            }
            screen.requestApplyInsets()
        }
        window.insetsController?.setSystemBarsAppearance(0,
            WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS)

        avatar = findViewById(R.id.nexus_avatar)
        presenceLabel = findViewById(R.id.home_presence)
        faceAvatar = faceScreen.findViewById<NexusAvatarView>(R.id.face_mode_avatar).apply { expanded = true }
        facePresenceLabel = faceScreen.findViewById(R.id.face_mode_presence)
        status = findViewById(R.id.home_status)
        transcript = findViewById(R.id.home_transcript)
        answer = findViewById(R.id.home_answer)
        playbackNotice = findViewById(R.id.home_playback_notice)
        input = findViewById(R.id.home_input)
        microphoneLevel = findViewById(R.id.home_microphone_level)
        if (BuildConfig.IS_PREVIEW) findViewById<TextView>(R.id.home_preview_warning).apply {
            setText(R.string.preview_warning)
            visibility = View.VISIBLE
        }

        fun bindButton(id: Int, disableWhileBusy: Boolean = true, action: () -> Unit): Button =
            findViewById<Button>(id).also {
                it.setOnClickListener { action() }
                if (disableWhileBusy) taskButtons.add(it)
            }

        voiceButton = bindButton(R.id.home_microphone, disableWhileBusy = false) { toggleVoice() }
        bindButton(R.id.home_face_mode) { setFaceMode(true) }
        faceScreen.findViewById<Button>(R.id.face_mode_exit).setOnClickListener { setFaceMode(false) }
        directVoiceButton = bindButton(R.id.home_speak_now, disableWhileBusy = false) {
            if (foreground && voiceActive && !busy) controller.listenForCommand()
        }
        findViewById<Switch>(R.id.home_spoken_replies).apply {
            isChecked = spokenRepliesEnabled
            setOnCheckedChangeListener { _, enabled ->
                voicePreferences.edit().putBoolean(SPOKEN_REPLIES_KEY, enabled).apply()
                controller.setSpokenRepliesEnabled(enabled)
            }
        }
        bindButton(R.id.home_send) {
            val message = input.text.toString().trim()
            if (message.isNotEmpty() && !busy) {
                input.setText("")
                getSystemService(InputMethodManager::class.java).hideSoftInputFromWindow(input.windowToken, 0)
                input.clearFocus()
                respond(message)
            }
        }
        bindButton(R.id.home_remember) {
            val content = input.text.toString().trim()
            if (content.isNotEmpty()) confirmNote(content)
        }
        retainButton = bindButton(R.id.home_retain) {
            pendingCandidate?.let { confirmNote(it.content, it.sensitive) }
        }
        bindButton(R.id.home_notes) { showNotes() }
        bindButton(R.id.home_voice_settings) { startActivity(Intent(this, VoiceSettingsActivity::class.java)) }
        bindButton(R.id.home_updates) { startActivity(Intent(this, UpdateActivity::class.java)) }
        bindButton(R.id.home_about) {
            runLocal(R.string.working, R.string.operation_failed, {
                assets.open("THIRD_PARTY_NOTICES.txt").bufferedReader().use { it.readText() }
            }) { notices ->
                if (foreground) showDialog(AlertDialog.Builder(this).setTitle(R.string.about)
                    .setMessage(getString(R.string.about_text, BuildConfig.VERSION_NAME) + "\n\n" + notices)
                    .setPositiveButton(R.string.close, null).create())
            }
        }
    }

    private fun setFaceMode(enabled: Boolean) {
        if (faceMode == enabled) return
        faceMode = enabled
        if (enabled && input.hasFocus()) {
            getSystemService(InputMethodManager::class.java).hideSoftInputFromWindow(input.windowToken, 0)
            input.clearFocus()
        }
        homeScroll.visibility = if (enabled) View.GONE else View.VISIBLE
        faceScreen.visibility = if (enabled) View.VISIBLE else View.GONE
        if (enabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val dispatcher = onBackInvokedDispatcher
            val callback = OnBackInvokedCallback { setFaceMode(false) }
            dispatcher.registerOnBackInvokedCallback(OnBackInvokedDispatcher.PRIORITY_DEFAULT, callback)
            unregisterFaceBack = { dispatcher.unregisterOnBackInvokedCallback(callback) }
        } else {
            unregisterFaceBack?.invoke()
            unregisterFaceBack = null
        }
        updateSystemBars()
        window.decorView.requestApplyInsets()
    }

    private fun updateSystemBars() {
        val insets = window.insetsController ?: return
        if (faceMode) {
            insets.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            insets.hide(WindowInsets.Type.systemBars())
        } else {
            insets.systemBarsBehavior = WindowInsetsController.BEHAVIOR_DEFAULT
            insets.show(WindowInsets.Type.systemBars())
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus && faceMode) updateSystemBars()
    }

    @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
    override fun onBackPressed() {
        if (faceMode) setFaceMode(false) else super.onBackPressed()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(FACE_MODE_STATE, faceMode)
    }

    private fun renderPresence() {
        val presence = when {
            speaking -> CompanionPresence.SPEAKING
            busy -> CompanionPresence.THINKING
            microphoneReady -> CompanionPresence.LISTENING
            else -> CompanionPresence.READY
        }
        if (avatar.presence == presence) return
        avatar.presence = presence
        faceAvatar.presence = presence
        for (label in listOf(presenceLabel, facePresenceLabel)) {
            label.setText(presence.label)
            label.setTextColor(getColor(presence.accent))
        }
    }

    private fun setBusy(active: Boolean) {
        busy = active
        renderPresence()
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
        else if (text != null) controller.playReply(text)
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
                }) { saved ->
                    answer.setText(if (saved.created) R.string.note_saved else R.string.note_already_saved)
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
            controller.stopListening()
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
        unregisterFaceBack?.invoke()
        unregisterFaceBack = null
        dialogs.toList().forEach { it.dismiss() }
        dialogs.clear()
        if (::controller.isInitialized) controller.close()
        worker.execute { brain.close() }
        worker.shutdown()
        super.onDestroy()
    }
}
