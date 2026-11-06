package fr.nexuscompanion.android

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.content.FileProvider
import java.util.concurrent.Executors

/** User-initiated, offline updates. Android owns final signature verification and consent. */
class UpdateActivity : Activity() {
    private lateinit var repository: LocalUpdateRepository
    private lateinit var status: TextView
    private lateinit var version: TextView
    private lateinit var choose: Button
    private lateinit var install: Button
    private val worker = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private var candidate: PreparedUpdate? = null
    private var busy = false
    private var destroyed = false
    private var foreground = false
    private var handedToInstaller = false

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        repository = LocalUpdateRepository.android(applicationContext)
        buildScreen()
        val token = state?.getString("update_token")
        val hash = state?.getString("update_hash")
        handedToInstaller = state?.getBoolean("update_handed", false) ?: false
        if (token != null && hash != null) runWorker({ repository.restore(token, hash) }) { prepared ->
            candidate = prepared
            showCandidate()
        }
    }

    private fun buildScreen() {
        fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(20), dp(24), dp(20), dp(20))
            setOnApplyWindowInsetsListener { view, insets ->
                val bars = insets.getInsets(WindowInsets.Type.systemBars())
                view.setPadding(bars.left + dp(20), bars.top + dp(20), bars.right + dp(20), bars.bottom + dp(20))
                insets
            }
        }
        fun label(text: String, size: Float): TextView = TextView(this).apply {
            this.text = text; textSize = size; setTextColor(Color.WHITE); gravity = Gravity.CENTER
            setPadding(0, dp(12), 0, dp(12)); root.addView(this, LinearLayout.LayoutParams(-1, -2))
        }
        fun button(text: Int, action: () -> Unit) = Button(this).apply {
            setText(text); setOnClickListener { action() }; root.addView(this, LinearLayout.LayoutParams(-1, -2))
        }
        label(getString(R.string.updates), 24f)
        label(getString(R.string.installed_version, BuildConfig.VERSION_NAME), 16f)
        label(getString(R.string.update_explanation), 17f)
        status = label(getString(R.string.update_choose_hint), 17f)
        version = label("", 18f)
        choose = button(R.string.choose_apk) { chooseApk() }
        install = button(R.string.install_update) { checkAndInstall() }.apply { isEnabled = false }
        button(R.string.close) { finish() }
        setContentView(ScrollView(this).apply {
            isFillViewport = true; setBackgroundColor(Color.rgb(10, 22, 43)); addView(root)
        })
        window.insetsController?.setSystemBarsAppearance(0,
            WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS)
        root.requestApplyInsets()
    }

    @Suppress("DEPRECATION")
    private fun chooseApk() {
        if (busy) return
        try {
            startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                type = APK_MIME
                addCategory(Intent.CATEGORY_OPENABLE)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }, PICK_APK)
        } catch (_: ActivityNotFoundException) { status.setText(R.string.update_picker_missing) }
    }

    @Deprecated("Android activity result callback")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == PICK_APK && resultCode == RESULT_OK) {
            val uri = data?.data
            discardUnsharedCandidate()
            candidate = null; version.text = ""; handedToInstaller = false
            setBusy(false)
            if (uri?.scheme != "content") { showProblem(UpdateProblem.INVALID); return }
            runWorker({
                val stream = contentResolver.openInputStream(uri) ?: throw UpdateRejected(UpdateProblem.READ_FAILED)
                repository.prepare(stream)
            }, discardOnDrop = true) { prepared ->
                candidate = prepared
                showCandidate()
            }
        } else if (requestCode == INSTALL_PERMISSION) {
            // Returning from Settings never installs automatically: a fresh tap is required.
            status.setText(if (packageManager.canRequestPackageInstalls()) R.string.update_permission_ready else R.string.update_permission_needed)
        }
    }

    private fun setBusy(active: Boolean) {
        busy = active
        choose.isEnabled = !active
        install.isEnabled = !active && candidate != null
    }

    private fun showCandidate() {
        version.text = getString(R.string.update_candidate_version, candidate?.metadata?.versionName.orEmpty())
        status.setText(if (handedToInstaller) R.string.update_handed_to_android else R.string.update_ready)
        setBusy(false)
    }

    private fun runWorker(task: () -> PreparedUpdate, discardOnDrop: Boolean = false, success: (PreparedUpdate) -> Unit) {
        if (busy || destroyed) return
        setBusy(true); status.setText(R.string.update_checking)
        worker.execute {
            try {
                val result = task()
                main.post {
                    if (destroyed) {
                        if (discardOnDrop) repository.discard(result.token)
                    } else {
                        setBusy(false)
                        success(result)
                    }
                }
            } catch (failure: Exception) {
                main.post {
                    if (!destroyed) {
                        discardUnsharedCandidate()
                        candidate = null; version.text = ""; handedToInstaller = false
                        setBusy(false)
                        showProblem((failure as? UpdateRejected)?.problem ?: UpdateProblem.READ_FAILED)
                    }
                }
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun checkAndInstall() {
        val selected = candidate ?: return
        if (busy) return
        if (!packageManager.canRequestPackageInstalls()) {
            status.setText(R.string.update_permission_needed)
            try {
                startActivityForResult(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:$packageName")), INSTALL_PERMISSION)
            } catch (_: ActivityNotFoundException) { status.setText(R.string.update_settings_missing) }
            return
        }
        runWorker({ repository.restore(selected.token, selected.sha256) }) { prepared ->
            candidate = prepared
            if (!foreground) { showCandidate(); return@runWorker }
            try {
                val uri = FileProvider.getUriForFile(this, "$packageName.updates", repository.file(prepared.token))
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, APK_MIME)
                    clipData = ClipData.newRawUri("Nexus Companion", uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                // The recipient receives only the checked private APK snapshot, never the notes.
                startActivity(intent)
                handedToInstaller = true
                status.setText(R.string.update_handed_to_android)
            } catch (_: ActivityNotFoundException) { status.setText(R.string.update_installer_missing) }
            catch (_: SecurityException) { status.setText(R.string.update_install_blocked) }
        }
    }

    private fun showProblem(problem: UpdateProblem) {
        status.setText(when (problem) {
            UpdateProblem.WRONG_APP -> R.string.update_wrong_app
            UpdateProblem.NOT_NEWER -> R.string.update_not_newer
            UpdateProblem.SIGNATURE -> R.string.update_wrong_signature
            UpdateProblem.ANDROID_VERSION -> R.string.update_android_version
            UpdateProblem.TOO_LARGE -> R.string.update_too_large
            UpdateProblem.CHANGED -> R.string.update_changed
            UpdateProblem.INVALID -> R.string.update_invalid
            UpdateProblem.READ_FAILED -> R.string.update_read_failed
        })
    }

    override fun onSaveInstanceState(outState: Bundle) {
        candidate?.let {
            outState.putString("update_token", it.token)
            outState.putString("update_hash", it.sha256)
            outState.putBoolean("update_handed", handedToInstaller)
        }
        super.onSaveInstanceState(outState)
    }

    private fun discardUnsharedCandidate() {
        if (!handedToInstaller) candidate?.let { repository.discard(it.token) }
    }

    override fun onResume() { super.onResume(); foreground = true }
    override fun onPause() { foreground = false; super.onPause() }

    override fun onDestroy() {
        destroyed = true
        worker.shutdownNow()
        if (!isChangingConfigurations) discardUnsharedCandidate()
        super.onDestroy()
    }

    companion object {
        internal const val APK_MIME = "application/vnd.android.package-archive"
        internal const val PICK_APK = 411
        internal const val INSTALL_PERMISSION = 412
    }
}
