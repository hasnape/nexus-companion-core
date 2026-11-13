package fr.nexuscompanion.android

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.ClipboardManager
import android.content.pm.PackageManager
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.text.SpannableString
import android.text.InputFilter
import android.text.InputType
import android.text.Spanned
import android.text.method.LinkMovementMethod
import android.text.style.URLSpan
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.window.OnBackInvokedCallback
import android.window.OnBackInvokedDispatcher
import androidx.annotation.StringRes
import java.util.concurrent.Executors
import java.text.DateFormat
import java.util.Date

/** Native UI. A single worker owns the packaged brain and private library operations. */
open class MainActivity : Activity() {
    private companion object {
        const val SPOKEN_REPLIES_KEY = "spoken_replies_enabled"
        const val ONLINE_ACCESS_KEY = "online_access_enabled"
        const val FACE_MODE_STATE = "face_mode"
        const val CHROME_READING_KEY = "chrome_ai_reading_enabled"
        const val ROBOT_MODE_STATE = "robot_mode"
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
    private lateinit var bodyControls: View
    private lateinit var bodyMotionStatus: TextView
    private lateinit var steeringButton: Button
    private val rollingBody = RollingBodyState()
    private var bodyGeneration = 0L
    private var steeringAtFront = true
    private val bodyTick = object : Runnable {
        override fun run() {
            if (!foreground || destroyed) return
            val frame = rollingBody.frame(SystemClock.elapsedRealtime()).copy(steeringAtFront = steeringAtFront)
            faceAvatar.bodyFrame = frame
            controller.setBodyMoving(frame.moving)
            bodyMotionStatus.text = getString(R.string.body_telemetry, frame.pose.travelledM * 100,
                Math.toDegrees(frame.pose.headingRad) % 360)
            if (frame.moving) main.postDelayed(this, 50L)
        }
    }
    private lateinit var status: TextView
    private lateinit var answer: TextView
    private lateinit var answerSource: TextView
    private lateinit var onlineAccess: Switch
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
    private var pendingBrowserReading: BrowserReading? = null
    private var taskEpoch = 0L
    private var voiceActive = false
    private var microphoneReady = false
    private var speaking = false
    private var speechOutput = SpeechOutputState.IDLE
    private var responsePlayback = false
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
    private val onlinePreferences by lazy { getSharedPreferences("nexus_online_preferences", MODE_PRIVATE) }
    private val audioRequestCode = 410
    private var privateDisplayed = false
    private var awaitingUserAccess: (() -> Unit)? = null
    private var sessionWasOpen = false
    private val accessTick = object : Runnable {
        override fun run() {
            if (!foreground || destroyed) return
            if (sessionWasOpen && !privateAccessAllowed()) lockPrivateViews()
            main.postDelayed(this, 1_000)
        }
    }
    private val learningIdle = object : Runnable {
        override fun run() {
            if (!foreground || destroyed) return
            if (!busy && !speaking && dialogDepth == 0 && LocalLearningJobService.enabled(this@MainActivity)) {
                worker.execute {
                    if (foreground && LocalLearningJobService.enabled(this@MainActivity)) {
                        try { brain.consolidate(foreground = true) } catch (_: Exception) { /* Retry later without altering notes. */ }
                    }
                }
            }
            main.postDelayed(this, 30_000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        volumeControlStream = AudioManager.STREAM_MUSIC
        library = createKnowledgeLibrary()
        brain = NativeBrainService(applicationContext, library, privateAccess = ::privateAccessAllowed)
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
                if (!active) stopBody()
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
                speechOutput = output
                if (output == SpeechOutputState.COMPLETED) {
                    val tracked = responsePlayback
                    responsePlayback = false
                    if (tracked && foreground && !destroyed) worker.execute { brain.replyPlayed() }
                } else if (output in setOf(SpeechOutputState.IDLE, SpeechOutputState.FAILED, SpeechOutputState.UNAVAILABLE, SpeechOutputState.INTERRUPTED)) {
                    responsePlayback = false
                }
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
        if (savedInstanceState != null) rollingBody.restore(RollingBodyState.Pose(
            savedInstanceState.getDouble("body_x"), savedInstanceState.getDouble("body_y"),
            savedInstanceState.getDouble("body_heading"), savedInstanceState.getDouble("body_travelled")))
        if (savedInstanceState?.getBoolean(FACE_MODE_STATE) == true) {
            faceAvatar.showBody = savedInstanceState.getBoolean(ROBOT_MODE_STATE)
            setFaceMode(true)
        }
    }

    internal open fun createVoiceInput(): VoiceInput = defaultVoiceInput(applicationContext)
    internal open fun createKnowledgeLibrary(): LocalKnowledgeLibrary = LocalKnowledgeLibrary(applicationContext)
    internal open fun privateAccessAllowed(): Boolean = UserAccessSession.allowed()
    internal open fun launchExternalApp(request: ExternalAppPolicy.Request): ExternalAppPolicy.LaunchResult = ExternalAppPolicy.launch(this, request)

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
        bodyControls = faceScreen.findViewById(R.id.body_controls)
        bodyMotionStatus = faceScreen.findViewById(R.id.body_motion_status)
        steeringButton = faceScreen.findViewById(R.id.body_steering)
        steeringAtFront = voicePreferences.getBoolean("steering_at_front", true)
        steeringButton.setText(if (steeringAtFront) R.string.body_steering_front else R.string.body_steering_rear)
        faceScreen.findViewById<Button>(R.id.body_stop).setOnClickListener { respond("stop") }
        steeringButton.setOnClickListener { configureSteering(!steeringAtFront) }
        val faceExit = faceScreen.findViewById<Button>(R.id.face_mode_exit)
        val fitFace = View.OnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> fitAvatarToControls() }
        listOf(faceAvatar, faceExit, facePresenceLabel, bodyControls).forEach { it.addOnLayoutChangeListener(fitFace) }
        status = findViewById(R.id.home_status)
        transcript = findViewById(R.id.home_transcript)
        answer = findViewById(R.id.home_answer)
        answerSource = findViewById<TextView>(R.id.home_answer_source).apply {
            movementMethod = LinkMovementMethod.getInstance()
        }
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
        bindButton(R.id.home_face_mode) { faceAvatar.showBody = false; setFaceMode(true) }
        bindButton(R.id.home_robot_mode) { faceAvatar.showBody = true; setFaceMode(true) }
        faceExit.setOnClickListener { setFaceMode(false) }
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
        val onlineBadge = findViewById<TextView>(R.id.home_network_mode)
        onlineAccess = findViewById<Switch>(R.id.home_online_access).apply {
            isChecked = onlinePreferences.getBoolean(ONLINE_ACCESS_KEY, true)
            onlineBadge.setText(if (isChecked) R.string.online_allowed else R.string.online_disabled_badge)
            setOnCheckedChangeListener { _, enabled ->
                onlinePreferences.edit().putBoolean(ONLINE_ACCESS_KEY, enabled).apply()
                onlineBadge.setText(if (enabled) R.string.online_allowed else R.string.online_disabled_badge)
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
            val browser = pendingBrowserReading
            if (browser != null) confirmBrowserNote(browser) else pendingCandidate?.let { confirmNote(it.content, it.sensitive) }
        }
        bindButton(R.id.home_notes) { showNotes() }
        bindButton(R.id.home_user_access) { requireUserAccess { showAnswer("Accès personnel ouvert pour cinq minutes. Le code se modifie dans l’écran sécurisé.") } }
        bindButton(R.id.home_lock_access) { UserAccessSession.lock(); lockPrivateViews(); showAnswer("Accès personnel verrouillé.") }
        bindButton(R.id.home_learning) { showLearning() }
        bindButton(R.id.home_local_model) { startActivity(Intent(this, LocalModelActivity::class.java)) }
        bindButton(R.id.home_voice_diagnostic) { startActivity(Intent(this, VoiceDiagnosticActivity::class.java)) }
        bindButton(R.id.home_voice_settings) { startActivity(Intent(this, VoiceSettingsActivity::class.java)) }
        findViewById<Switch>(R.id.home_chrome_auto_read).apply {
            isChecked = onlinePreferences.getBoolean(CHROME_READING_KEY, false)
            setOnCheckedChangeListener { _, enabled ->
                if (enabled) showChromeReaderSetup() else {
                    onlinePreferences.edit().putBoolean(CHROME_READING_KEY, false).apply()
                    ChromeAiReaderService.cancelReading()
                }
            }
        }
        bindButton(R.id.home_chrome_read) { readChrome() }
        bindButton(R.id.home_chrome_paste) { readCopiedText() }
        bindButton(R.id.home_updates) { startActivity(Intent(this, UpdateActivity::class.java)) }
        bindButton(R.id.home_about) {
            runTask(R.string.working, R.string.operation_failed, {
                assets.open("THIRD_PARTY_NOTICES.txt").bufferedReader().use { it.readText() }
            }) { notices ->
                if (foreground) showDialog(AlertDialog.Builder(this).setTitle(R.string.about)
                    .setMessage(getString(R.string.about_text, BuildConfig.VERSION_NAME) + "\n\n" + notices)
                    .setPositiveButton(R.string.close, null).create())
            }
        }
    }

    private fun setFaceMode(enabled: Boolean) {
        if (!enabled || !faceAvatar.showBody) stopBody()
        bodyControls.visibility = if (enabled && faceAvatar.showBody) View.VISIBLE else View.GONE
        faceScreen.post { if (!destroyed) fitAvatarToControls() }
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

    private fun fitAvatarToControls() {
        val exit = faceScreen.findViewById<Button>(R.id.face_mode_exit)
        faceAvatar.setFaceControls(exit.left - faceAvatar.left, exit.top - faceAvatar.top,
            exit.right - faceAvatar.left, exit.bottom - faceAvatar.top,
            (if (bodyControls.visibility == View.VISIBLE) bodyControls.top else facePresenceLabel.top) - faceAvatar.top)
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
        outState.putBoolean(ROBOT_MODE_STATE, faceAvatar.showBody)
        val pose = rollingBody.frame(SystemClock.elapsedRealtime()).pose
        outState.putDouble("body_x", pose.xM); outState.putDouble("body_y", pose.yM)
        outState.putDouble("body_heading", pose.headingRad); outState.putDouble("body_travelled", pose.travelledM)
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
        onlineAccess.isEnabled = !active
        input.isEnabled = !active
        directVoiceButton.isEnabled = voiceActive && !active
    }

    private fun restoreStatus() {
        val message = voiceStatus
        if (voiceActive && message != null) status.text = message else status.setText(R.string.local_ready)
    }

    private fun <T> runTask(
        progress: Int,
        failureMessage: Int,
        task: () -> T,
        voiceReply: (T) -> String? = { null },
        expectAnswer: (T) -> Boolean = { false },
        success: (T) -> Unit
    ) {
        if (busy || destroyed) return
        setBusy(true)
        controller.beginResponse()
        status.setText(progress)
        val taskToken = taskEpoch
        worker.execute {
            try {
                val result = task()
                main.post {
                    if (!destroyed) {
                        setBusy(false)
                        restoreStatus()
                        if (!foreground || taskToken != taskEpoch) {
                            if (foreground) showAnswer(if (result is NativeBrainService.Reply && result.external != null) getString(R.string.external_cancelled)
                                else "Traitement interrompu lors de la mise en arrière-plan. Repose ta question.")
                            return@post
                        }
                        success(result)
                        finishLocalVoice(voiceReply(result), expectAnswer(result), trackContext = true)
                    }
                }
            } catch (failure: Exception) {
                main.post {
                    if (!destroyed) {
                        setBusy(false)
                        if (!foreground || taskToken != taskEpoch) return@post
                        showAnswer(when (failure) {
                            is InvalidKnowledgeNote -> R.string.note_invalid
                            is KnowledgeCapacityReached -> R.string.library_full
                            is KnowledgeChanged -> R.string.note_changed
                            else -> failureMessage
                        })
                        restoreStatus()
                        finishLocalVoice(null)
                    }
                }
            }
        }
    }

    private fun finishLocalVoice(text: String?, expectAnswer: Boolean = false, trackContext: Boolean = false) {
        responsePlayback = false
        if (!foreground || dialogDepth > 0) return
        if (voiceActive) controller.finishResponse(text, expectAnswer)
        else if (text != null) controller.playReply(text)
        responsePlayback = trackContext && !text.isNullOrBlank() && findViewById<Switch>(R.id.home_spoken_replies).isChecked &&
            speechOutput in setOf(SpeechOutputState.PREPARING, SpeechOutputState.PLAYING)
    }

    private fun respond(message: String) {
        if (message.isBlank() || message.length > NativeCapacity.MAX_MESSAGE_CHARS) return
        if (VoiceTranscriptPolicy.isStopCommand(message)) {
            stopBody()
            worker.execute { brain.clearConversation() }
            showAnswer(R.string.body_stopped)
            controller.beginResponse()
            finishLocalVoice(getString(R.string.body_stopped), expectAnswer = true)
            return
        }
        if (busy) return
        if (ChromeAiText.isReadCommand(message)) { readChrome(); return }
        if (VoiceTranscriptPolicy.commandWords(message) in setOf("lis le texte copie", "lis le texte que j ai copie")) {
            readCopiedText(); return
        }
        when (VoiceTranscriptPolicy.commandWords(message)) {
            "roue directrice a l avant", "direction a l avant" -> { configureSteering(true); return }
            "roue directrice a l arriere", "direction a l arriere" -> { configureSteering(false); return }
        }
        pendingCandidate = null
        pendingBrowserReading = null
        retainButton.visibility = View.GONE
        val display = when (VoiceTranscriptPolicy.commandWords(message)) {
            "mode robot", "passe en mode robot", "affiche ton corps" -> "robot"
            "mode plein visage", "passe en mode plein visage", "affiche ton visage" -> "face"
            "quitte le mode robot", "quitte le mode plein visage", "retour a l accueil" -> "home"
            else -> null
        }
        if (display != null) {
            stopBody()
            worker.execute { brain.clearConversation() }
            faceAvatar.showBody = display == "robot"
            setFaceMode(display != "home")
            val text = getString(when (display) { "robot" -> R.string.full_robot_mode; "face" -> R.string.full_face_mode; else -> R.string.back_to_home })
            showAnswer(text)
            controller.beginResponse()
            finishLocalVoice(text, expectAnswer = true)
            return
        }
        val onlineEnabled = onlineAccess.isChecked
        val axle = if (steeringAtFront) "front" else "rear"
        val generation = bodyGeneration
        var externalFeedback: String? = null
        fun replyText(reply: NativeBrainService.Reply): String = if (reply.protectedInformation && (!foreground || !privateAccessAllowed())) "Accès personnel verrouillé. Saisis le code utilisateur pour continuer."
            else if (reply.external != null && (!foreground || generation != bodyGeneration)) getString(R.string.external_cancelled)
            else if (reply.body != null && generation != bodyGeneration) getString(R.string.body_stopped)
            else if (reply.notesAvailable) reply.text else getString(R.string.reply_with_library_warning, reply.text, getString(R.string.library_read_failed))
        runTask(R.string.thinking, R.string.brain_failed, { brain.reply(message, onlineEnabled, axle) },
            voiceReply = { if (it.external != null) externalFeedback else replyText(it) }, expectAnswer = { it.expectsAnswer }) { reply ->
            if (reply.requiresUserCode || (reply.protectedInformation && !privateAccessAllowed())) {
                showAnswer("Le code utilisateur est nécessaire pour consulter ces informations personnelles.")
                if (foreground) requireUserAccess { respond(message) }
                return@runTask
            }
            if (reply.protectedInformation && !foreground) return@runTask
            privateDisplayed = reply.protectedInformation
            val text = replyText(reply)
            showAnswer(text, reply.source)
            if (reply.evidenceSources.isNotEmpty() || reply.generatedLocally) {
                findViewById<TextView>(R.id.home_answer_source).apply {
                    this.text = if (reply.generatedLocally) "Réponse du modèle local · à vérifier" else
                        "Notes utilisées : " + reply.evidenceSources.joinToString(", ") { it.title }
                    visibility = View.VISIBLE
                }
            }
            if (reply.external != null && foreground && generation == bodyGeneration) {
                stopBody()
                worker.execute { brain.clearConversation() }
                val readingRequested = reply.external.app == ExternalAppPolicy.App.CHROME &&
                    reply.external.operation == ExternalAppPolicy.Operation.SEARCH &&
                    onlinePreferences.getBoolean(CHROME_READING_KEY, false) && beginChromeReading(reply.external.target)
                when (launchExternalApp(reply.external)) {
                    ExternalAppPolicy.LaunchResult.PREFERRED -> controller.stop()
                    ExternalAppPolicy.LaunchResult.FALLBACK -> {
                        if (readingRequested) ChromeAiReaderService.cancelReading()
                        showAnswer(getString(R.string.external_app_fallback, reply.external.app.label))
                        controller.stop()
                    }
                    ExternalAppPolicy.LaunchResult.UNAVAILABLE -> {
                        if (readingRequested) ChromeAiReaderService.cancelReading()
                        externalFeedback = getString(R.string.external_app_unavailable)
                        showAnswer(externalFeedback!!)
                    }
                }
            }
            if (reply.editNote != null && foreground) editNote(reply.editNote)
            if (reply.body != null && foreground && generation == bodyGeneration) {
                faceAvatar.showBody = true
                setFaceMode(true)
                rollingBody.start(reply.body, SystemClock.elapsedRealtime())
                main.removeCallbacks(bodyTick)
                bodyTick.run()
            }
            showBrowserSource(reply.browserReading)
            pendingCandidate = if (reply.notesAvailable) reply.candidate else null
            pendingBrowserReading = reply.browserReading
            retainButton.visibility = if (pendingCandidate != null || pendingBrowserReading != null) View.VISIBLE else View.GONE
        }
    }

    internal open fun beginChromeReading(expectedQuery: String?): Boolean = ChromeAiReaderService.begin(expectedQuery)

    internal open fun launchChromeForReading(): Boolean = try {
        val intent = packageManager.getLaunchIntentForPackage(ChromeAiReaderService.CHROME)
        if (intent == null) false else { startActivity(intent); true }
    } catch (_: RuntimeException) { false }

    private fun showChromeReaderSetup() {
        val toggle = findViewById<Switch>(R.id.home_chrome_auto_read)
        val wasEnabled = onlinePreferences.getBoolean(CHROME_READING_KEY, false)
        showDialog(AlertDialog.Builder(this).setTitle(R.string.chrome_reader_title).setMessage(R.string.chrome_reader_setup)
            .setNegativeButton(R.string.cancel) { _, _ -> toggle.isChecked = wasEnabled }
            .setOnCancelListener { toggle.isChecked = wasEnabled }
            .setPositiveButton(R.string.chrome_reader_configure) { _, _ ->
                onlinePreferences.edit().putBoolean(CHROME_READING_KEY, true).apply()
                // Avoid a second setup dialog when synchronizing the visual switch.
                toggle.setOnCheckedChangeListener(null)
                toggle.isChecked = true
                toggle.setOnCheckedChangeListener { _, enabled ->
                    if (enabled) showChromeReaderSetup() else {
                        onlinePreferences.edit().putBoolean(CHROME_READING_KEY, false).apply()
                        ChromeAiReaderService.cancelReading()
                    }
                }
                try { startActivity(ChromeAiReaderService.settingsIntent(this)) }
                catch (_: RuntimeException) {
                    try { startActivity(ChromeAiReaderService.fallbackSettingsIntent()) }
                    catch (_: RuntimeException) { showAnswer(R.string.chrome_no_response) }
                }
            }.create())
    }

    private fun readChrome() {
        if (busy || !foreground) return
        if (!onlineAccess.isChecked) { sayBrowserStatus(R.string.external_online_disabled); return }
        if (!onlinePreferences.getBoolean(CHROME_READING_KEY, false) || !beginChromeReading(null)) {
            showChromeReaderSetup()
            return
        }
        stopBody()
        pendingBrowserReading = null
        pendingCandidate = null
        retainButton.visibility = View.GONE
        worker.execute { brain.clearConversation() }
        controller.stop()
        showAnswer(R.string.chrome_read_waiting)
        if (!launchChromeForReading()) {
            ChromeAiReaderService.cancelReading()
            if (autoListenEnabled) activateVoice()
            sayBrowserStatus(R.string.external_app_unavailable)
        }
    }

    private fun sayBrowserStatus(@StringRes message: Int) {
        pendingCandidate = null
        pendingBrowserReading = null
        retainButton.visibility = View.GONE
        worker.execute { brain.clearConversation() }
        showAnswer(message)
        controller.beginResponse()
        finishLocalVoice(getString(message), expectAnswer = true)
    }

    private fun readCopiedText() {
        if (!privateAccessAllowed()) { requireUserAccess { readCopiedText() }; return }
        if (busy || !foreground) return
        val text = try {
            val clipboard = getSystemService(ClipboardManager::class.java)
            val clip = clipboard.primaryClip
            if (clip == null || clip.itemCount != 1) null else clip.getItemAt(0).text?.toString()
        } catch (_: RuntimeException) { null }
        val reading = text?.let(BrowserReading::copied)
        if (reading == null) { sayBrowserStatus(R.string.chrome_clipboard_empty); return }
        showDialog(AlertDialog.Builder(this).setTitle(R.string.chrome_read_copied)
            .setMessage(getString(R.string.chrome_copy_review, reading.text)).setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.chrome_read_confirm) { _, _ ->
                // Dialog dismissal restores the normal voice lifecycle before the worker completes.
                main.post { if (foreground && !destroyed) readBrowserResult(reading) }
            }.create())
    }

    private fun readBrowserResult(reading: BrowserReading) {
        pendingCandidate = null
        pendingBrowserReading = null
        retainButton.visibility = View.GONE
        val generation = bodyGeneration
        runTask(R.string.working, R.string.brain_failed, { brain.readBrowserResult(reading) },
            voiceReply = { if (foreground && generation == bodyGeneration && (!it.protectedInformation || privateAccessAllowed())) it.text else null }, expectAnswer = { true }) { reply ->
            if (reply.requiresUserCode || (reply.protectedInformation && !privateAccessAllowed())) {
                if (foreground) requireUserAccess { readBrowserResult(reading) }
                return@runTask
            }
            if (foreground && generation == bodyGeneration) {
                privateDisplayed = reply.protectedInformation
                showAnswer(reply.text)
                showBrowserSource(reply.browserReading)
                pendingBrowserReading = reply.browserReading.takeIf { reply.notesAvailable }
                retainButton.visibility = if (pendingBrowserReading != null) View.VISIBLE else View.GONE
            }
        }
    }

    private fun showBrowserSource(reading: BrowserReading?) {
        if (reading == null) return
        answerSource.text = if (reading.source == "external:clipboard") getString(R.string.chrome_copied_source)
            else SpannableString(getString(R.string.chrome_read_source)).apply {
                setSpan(URLSpan(reading.source), 0, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
        answerSource.visibility = View.VISIBLE
    }

    private fun confirmBrowserNote(reading: BrowserReading) {
        showDialog(AlertDialog.Builder(this).setTitle(R.string.confirm_note_title)
            .setMessage(getString(R.string.confirm_note_message, reading.text)).setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.save_note) { _, _ ->
                runTask(R.string.working, R.string.library_write_failed,
                    { library.addApprovedSource(reading.title, reading.text, reading.source) }) { saved ->
                    pendingBrowserReading = null
                    retainButton.visibility = View.GONE
                    showAnswer(if (saved.created) R.string.note_saved else R.string.note_already_saved)
                }
            }.create())
    }

    private fun stopBody() {
        bodyGeneration++
        main.removeCallbacks(bodyTick)
        val frame = rollingBody.stop(SystemClock.elapsedRealtime()).copy(steeringAtFront = steeringAtFront)
        if (::faceAvatar.isInitialized) faceAvatar.bodyFrame = frame
        if (::controller.isInitialized) controller.setBodyMoving(false)
        if (::bodyMotionStatus.isInitialized) bodyMotionStatus.setText(R.string.body_stopped)
    }

    private fun configureSteering(front: Boolean) {
        stopBody()
        steeringAtFront = front
        voicePreferences.edit().putBoolean("steering_at_front", front).apply()
        steeringButton.setText(if (front) R.string.body_steering_front else R.string.body_steering_rear)
        faceAvatar.bodyFrame = faceAvatar.bodyFrame.copy(steeringAtFront = front)
        worker.execute { brain.clearConversation() }
        val text = getString(if (front) R.string.body_steering_front else R.string.body_steering_rear)
        showAnswer(text)
        controller.beginResponse()
        finishLocalVoice(text, expectAnswer = true)
    }

    private fun showAnswer(@StringRes text: Int) = showAnswer(getString(text))

    private fun showAnswer(text: String, source: WikipediaKnowledgeSearch.Article? = null) {
        answer.text = text
        answerSource.text = source?.let { article ->
            SpannableString(getString(R.string.online_source, article.title)).apply {
                setSpan(URLSpan(article.url), 0, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
        }
        answerSource.visibility = if (source == null) View.GONE else View.VISIBLE
    }

    private fun confirmNote(content: String, sensitive: Boolean = false) {
        worker.execute { brain.clearConversation() }
        showDialog(AlertDialog.Builder(this).setTitle(R.string.confirm_note_title)
            .setMessage(getString(if (sensitive) R.string.confirm_sensitive_note_message else R.string.confirm_note_message, content))
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.save_note) { _, _ ->
                runTask(R.string.working, R.string.library_write_failed, {
                    library.addUserNote(content.take(80), content)
                }) { saved ->
                    showAnswer(if (saved.created) R.string.note_saved else R.string.note_already_saved)
                    if (input.text.toString().trim() == content) input.setText("")
                    if (pendingCandidate?.content == content) {
                        pendingCandidate = null; retainButton.visibility = View.GONE
                    }
                }
            }.create())
    }

    private fun showNotes(page: Int = 0) {
        if (!privateAccessAllowed()) { requireUserAccess { showNotes(page) }; return }
        val query = input.text.toString().trim()
        runTask(R.string.working, R.string.library_read_failed, {
            if (query.isBlank()) library.list().sortedByDescending { it.recordedAt } else library.search(query)
        }) { notes ->
            if (!privateAccessAllowed()) return@runTask
            if (notes.isEmpty()) { showAnswer(R.string.library_no_results); return@runTask }
            val start = (page * 50).coerceAtMost(((notes.size - 1) / 50) * 50)
            val shown = notes.drop(start).take(50)
            if (foreground) {
                val dialog = AlertDialog.Builder(this)
                    .setTitle(getString(R.string.notes_page, start + 1, start + shown.size, notes.size))
                    .setItems(shown.map { it.title }.toTypedArray()) { _, position -> showNote(shown[position]) }
                    .setNegativeButton(R.string.close, null)
                if (start + shown.size < notes.size) dialog.setPositiveButton(R.string.notes_next) { _, _ -> showNotes(page + 1) }
                if (start > 0) dialog.setNeutralButton(R.string.notes_previous) { _, _ -> showNotes(page - 1) }
                showDialog(dialog.create())
            }
        }
    }

    private fun showNote(note: LocalKnowledgeLibrary.Entry) {
        if (!privateAccessAllowed()) { requireUserAccess { showNote(note) }; return }
        val details = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val pad = (20 * resources.displayMetrics.density).toInt()
            setPadding(pad, pad, pad, pad)
        }
        details.addView(TextView(this).apply {
            text = getString(R.string.library_entry, note.title, note.excerpt, note.source)
            textSize = 18f
        })
        if (note.manuallyEdited) details.addView(TextView(this).apply { setText(R.string.note_manually_edited) })
        val history = Button(this).apply { id = R.id.note_history_button; setText(R.string.note_history) }
        details.addView(history)
        val dialog = AlertDialog.Builder(this).setTitle(note.title)
            .setView(ScrollView(this).apply { addView(details) })
            .setNegativeButton(R.string.close, null)
            .setPositiveButton(R.string.edit_note) { _, _ -> editNote(note) }
            .setNeutralButton(R.string.delete_note) { _, _ ->
                showDialog(AlertDialog.Builder(this).setTitle(R.string.delete_note)
                    .setMessage(R.string.delete_note_confirmation).setNegativeButton(R.string.cancel, null)
                    .setPositiveButton(R.string.delete_note) { _, _ ->
                        runTask(R.string.working, R.string.library_write_failed, { library.remove(note.id, note) }) { removed ->
                            showAnswer(if (removed) R.string.note_deleted else R.string.library_no_results)
                        }
                    }.create())
            }.create()
        history.setOnClickListener { showHistory(note); dialog.dismiss() }
        showDialog(dialog)
    }

    private fun editNote(note: LocalKnowledgeLibrary.Entry) {
        if (!privateAccessAllowed()) { requireUserAccess { editNote(note) }; return }
        val fields = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val pad = (20 * resources.displayMetrics.density).toInt()
            setPadding(pad, pad, pad, pad)
        }
        val title = EditText(this).apply {
            id = R.id.note_edit_title; setHint(R.string.note_title)
            setSingleLine(true); filters = arrayOf(InputFilter.LengthFilter(160)); setText(note.title)
        }
        val content = EditText(this).apply {
            id = R.id.note_edit_body; setHint(R.string.note_content)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            minLines = 6; maxLines = 12
            filters = arrayOf(InputFilter.LengthFilter(NativeCapacity.MAX_NOTE_CHARS)); setText(note.excerpt)
        }
        fields.addView(title); fields.addView(content)
        showDialog(AlertDialog.Builder(this).setTitle(R.string.edit_note)
            .setView(ScrollView(this).apply { addView(fields) })
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.save_note) { _, _ ->
                val editedTitle = title.text.toString(); val editedContent = content.text.toString()
                runTask(R.string.working, R.string.library_write_failed, { library.replace(note, editedTitle, editedContent) }) { result ->
                    showAnswer(if (result.created) R.string.note_updated else R.string.note_unchanged)
                }
            }.create())
    }

    private fun showHistory(current: LocalKnowledgeLibrary.Entry) {
        if (!privateAccessAllowed()) { requireUserAccess { showHistory(current) }; return }
        runTask(R.string.working, R.string.library_read_failed, { library.history(current.id) }) { versions ->
            if (!privateAccessAllowed()) return@runTask
            if (versions.isEmpty()) { showAnswer(R.string.note_history_empty); return@runTask }
            if (foreground) showDialog(AlertDialog.Builder(this).setTitle(R.string.note_history)
                .setItems(versions.map { DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(it.recordedAt)) }.toTypedArray()) { _, index ->
                    val old = versions[index]
                    showDialog(AlertDialog.Builder(this).setTitle(R.string.note_previous_version)
                        .setMessage(getString(R.string.library_entry, old.title, old.excerpt, old.source))
                        .setNegativeButton(R.string.close, null)
                        .setPositiveButton(R.string.note_restore) { _, _ ->
                            showDialog(AlertDialog.Builder(this).setTitle(R.string.note_restore)
                                .setMessage(getString(R.string.note_restore_confirmation, old.excerpt))
                                .setNegativeButton(R.string.cancel, null)
                                .setPositiveButton(R.string.note_restore) { _, _ ->
                                    runTask(R.string.working, R.string.library_write_failed,
                                        { library.replace(current, old.title, old.excerpt) }) { restored ->
                                        showAnswer(if (restored.created) R.string.note_restored else R.string.note_unchanged)
                                    }
                                }.create())
                        }.create())
                }.setNegativeButton(R.string.close, null).create())
        }
    }

    /** Notes can contain private text: keep the microphone off while any dialog is visible. */
    private fun showDialog(dialog: AlertDialog) {
        dialog.window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        if (dialogDepth == 0) {
            stopBody()
            worker.execute { brain.clearConversation() }
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

    @Suppress("DEPRECATION")
    private fun requireUserAccess(action: () -> Unit) {
        if (!foreground || destroyed) return
        if (privateAccessAllowed()) {
            sessionWasOpen = true
            window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
            action(); return
        }
        if (awaitingUserAccess != null) return
        awaitingUserAccess = action
        controller.stop()
        startActivityForResult(Intent(this, UserCodeActivity::class.java), 423)
    }

    @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 423) {
            val action = awaitingUserAccess; awaitingUserAccess = null
            if (resultCode == RESULT_OK && privateAccessAllowed()) main.post {
                if (foreground && !destroyed && privateAccessAllowed()) requireUserAccess { action?.invoke() }
            }
        }
    }

    private fun lockPrivateViews() {
        val wasPrivate = sessionWasOpen || privateDisplayed
        sessionWasOpen = false
        if (wasPrivate) {
            dialogs.toList().forEach { it.dismiss() }
            if (::controller.isInitialized) controller.stop()
            if (::answer.isInitialized) {
                showAnswer("Accès personnel verrouillé.")
                transcript.text = ""; transcript.visibility = View.GONE; input.setText("")
                pendingCandidate = null; pendingBrowserReading = null; retainButton.visibility = View.GONE
            }
            privateDisplayed = false
            if (::brain.isInitialized && !destroyed) worker.execute { brain.clearConversation() }
        }
        // Keep protected content out of overview snapshots even while the app is paused.
        if (foreground) window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        if (wasPrivate && foreground && autoListenEnabled && dialogDepth == 0) main.post {
            if (foreground && !destroyed && !voiceActive && dialogDepth == 0) activateVoice()
        }
    }

    private fun showLearning() {
        runTask(R.string.working, R.string.operation_failed, {
            val device = DeviceResources.inspect(applicationContext)
            DeviceResources.describe(device, DeviceResources.budget(device)) to brain.learningStatistics()
        }) { (description, stats) ->
            if (!foreground) return@runTask
            val panel = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(36, 20, 36, 12) }
            panel.addView(TextView(this).apply {
                textSize = 16f
                text = getString(R.string.learning_description, description, stats.consolidated, stats.remaining, stats.passed, stats.exercises)
            })
            panel.addView(Switch(this).apply {
                setText(R.string.learning_automatic); minHeight = 48
                isChecked = LocalLearningJobService.enabled(this@MainActivity)
                setOnCheckedChangeListener { _, enabled ->
                    getSharedPreferences("nexus_learning_preferences", MODE_PRIVATE).edit()
                        .putBoolean("local_consolidation_enabled", enabled).apply()
                    LocalLearningJobService.schedule(this@MainActivity)
                }
            })
            showDialog(AlertDialog.Builder(this).setTitle("Matériel et apprentissage")
                .setView(ScrollView(this).apply { addView(panel) }).setNegativeButton(R.string.close, null)
                .setPositiveButton("Consolider maintenant") { _, _ ->
                    runTask(R.string.working, R.string.operation_failed, { brain.consolidate(foreground = true) }) {
                        showAnswer("${it.consolidated} notes consolidées ; ${it.remaining} à traiter. Les travaux automatiques continuent au repos et pendant la charge, selon les ressources disponibles.")
                    }
                }.create())
        }
    }

    private fun toggleVoice() {
        if (voiceActive) {
            stopBody()
            autoListenEnabled = false
            voicePreferences.edit().putBoolean("auto_listen_enabled", false).apply()
            controller.stopListening()
            worker.execute { brain.clearConversation() }
        } else if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            autoListenEnabled = true
            voicePreferences.edit().putBoolean("auto_listen_enabled", true).apply()
            activateVoice()
        } else requestMicrophonePermission()
    }

    override fun onResume() {
        super.onResume()
        foreground = true
        sessionWasOpen = privateAccessAllowed()
        if (sessionWasOpen) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        main.removeCallbacks(accessTick); accessTick.run()
        main.removeCallbacks(learningIdle); main.postDelayed(learningIdle, 5_000)
        LocalLearningJobService.schedule(this)
        bodyTick.run()
        val wasReading = ChromeAiReaderService.session.pending() != null
        val chromeResult = ChromeAiReaderService.session.consume()
        var chromeStatus: Int? = null
        if (chromeResult?.reading != null && onlinePreferences.getBoolean(CHROME_READING_KEY, false) && onlineAccess.isChecked) {
            readBrowserResult(chromeResult.reading)
        } else if (chromeResult != null) chromeStatus = R.string.chrome_no_response
        else if (wasReading) chromeStatus = R.string.chrome_read_cancelled
        if (autoListenEnabled && !voiceActive && !permissionRequestInFlight && dialogDepth == 0) {
            if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) activateVoice()
            else if (!voicePreferences.getBoolean("microphone_permission_prompted", false)) requestMicrophonePermission()
            else status.setText(R.string.microphone_permission_needed)
        }
        chromeStatus?.let(::sayBrowserStatus)
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
        taskEpoch++
        if (::brain.isInitialized) brain.cancelGeneration()
        foreground = false
        main.removeCallbacks(accessTick); main.removeCallbacks(learningIdle)
        UserAccessSession.lock()
        lockPrivateViews()
        pendingBrowserReading = null
        stopBody()
        if (::controller.isInitialized) controller.stop()
        if (::brain.isInitialized && !destroyed) worker.execute { brain.clearConversation() }
        super.onPause()
    }

    override fun onDestroy() {
        taskEpoch++
        if (::brain.isInitialized) brain.cancelGeneration()
        if (isFinishing) ChromeAiReaderService.cancelReading()
        destroyed = true
        awaitingUserAccess = null
        main.removeCallbacks(accessTick); main.removeCallbacks(learningIdle)
        stopBody()
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
