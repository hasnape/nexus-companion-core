package fr.nexuscompanion.android

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.text.InputFilter
import android.text.InputType
import android.view.View
import android.view.WindowInsets
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.util.concurrent.Executors

/** Only encrypted archives leave this screen; the PIN and Keystore keys stay on this device. */
open class BrainBackupActivity : Activity() {
    private val worker = Executors.newSingleThreadExecutor()
    private lateinit var library: LocalKnowledgeLibrary
    private lateinit var status: TextView
    private lateinit var secret: EditText
    private lateinit var confirmation: EditText
    private lateinit var exportButton: Button
    private lateinit var previewButton: Button
    private lateinit var restoreButton: Button
    private lateinit var undoButton: Button
    @Volatile private var foreground = false
    @Volatile private var destroyed = false
    private var busy = false
    private var wasUnlocked = false
    private val main = android.os.Handler(android.os.Looper.getMainLooper())
    private val accessTick = object : Runnable {
        override fun run() {
            if (!foreground || destroyed) return
            if (wasUnlocked && !UserAccessSession.allowed()) {
                clearPrivatePreview(); status.setText(R.string.backup_locked); wasUnlocked = false; refresh()
            }
            main.postDelayed(this, 1000)
        }
    }
    @Volatile private var epoch = 0L
    private var encryptedImport: ByteArray? = null
    private var encryptedExport: ByteArray? = null
    private var preview: ByteArray? = null
    private var expectedFingerprint: String? = null
    private var dialog: AlertDialog? = null
    internal open fun createLibrary() = LocalKnowledgeLibrary(applicationContext)

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        title = getString(R.string.backup_title)
        library = createLibrary()
        val panel = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(28, 28, 28, 28) }
        setContentView(ScrollView(this).apply {
            addView(panel)
            setOnApplyWindowInsetsListener { view, insets ->
                val bars = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.ime())
                view.setPadding(bars.left, bars.top, bars.right, bars.bottom); insets
            }
            requestApplyInsets()
        })
        TextView(this).apply { setText(R.string.backup_explanation); textSize = 16f; panel.addView(this) }
        status = TextView(this).also { it.textSize = 16f; panel.addView(it) }
        fun button(label: Int, action: () -> Unit) = Button(this).also { b -> b.setText(label); b.setOnClickListener { action() }; panel.addView(b) }
        button(R.string.backup_unlock) { startActivityForResult(Intent(this, UserCodeActivity::class.java), 470) }
        fun field(label: Int) = EditText(this).apply {
            setHint(label); inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            filters = arrayOf(InputFilter.LengthFilter(128)); setSingleLine(true); isSaveEnabled = false
            importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS; panel.addView(this)
        }
        secret = field(R.string.backup_secret)
        confirmation = field(R.string.backup_confirm_secret)
        exportButton = button(R.string.backup_export) { export() }
        button(R.string.backup_choose) {
            startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*"), 472)
        }
        previewButton = button(R.string.backup_preview) { previewImport() }
        restoreButton = button(R.string.backup_restore) { confirmRestore() }
        undoButton = button(R.string.backup_undo) {
            runWork({ token -> authorize(token); library.undoRestore { authorize(token) }; true }) { status.setText(R.string.backup_undo_done) }
        }
        refresh()
    }
    private fun refresh() {
        if (!::secret.isInitialized) return
        val allowed = foreground && UserAccessSession.allowed() && !busy
        if (allowed) wasUnlocked = true
        secret.isEnabled = allowed; confirmation.isEnabled = allowed
        exportButton.isEnabled = allowed; previewButton.isEnabled = allowed && encryptedImport != null
        restoreButton.isEnabled = allowed && preview != null; undoButton.isEnabled = allowed
    }
    private fun authorize(token: Long) {
        check(!destroyed && foreground && token == epoch && UserAccessSession.allowed()) { "Private session closed" }
    }
    private fun phrase(confirm: Boolean): CharArray? {
        if (!UserAccessSession.allowed()) { status.setText(R.string.backup_locked); return null }
        val value = secret.text.toString().toCharArray()
        val repeated = confirmation.text.toString().toCharArray()
        secret.setText(""); confirmation.setText("")
        val valid = value.size in 12..128 && value.any { !it.isWhitespace() } && (!confirm || value.contentEquals(repeated))
        repeated.fill('\u0000')
        if (!valid) { value.fill('\u0000'); status.setText(R.string.backup_bad_secret); return null }
        return value
    }
    private fun <T> runWork(task: (Long) -> T, success: (T) -> Unit) {
        if (busy || destroyed || !foreground || !UserAccessSession.allowed()) return
        val token = epoch; busy = true; refresh(); status.setText(R.string.backup_working)
        worker.execute {
            try {
                val result = task(token)
                runOnUiThread {
                    if (!destroyed) {
                        busy = false
                        if (token == epoch && foreground && UserAccessSession.allowed()) success(result)
                        else discard(result)
                        refresh()
                    } else discard(result)
                }
            } catch (failure: Exception) {
                runOnUiThread {
                    if (!destroyed) {
                        busy = false
                        if (foreground) status.setText(if (failure is KnowledgeChanged) R.string.backup_changed else R.string.backup_failed)
                        refresh()
                    }
                }
            }
        }
    }
    private data class RestorePreview(val data: ByteArray, val counts: LocalKnowledgeLibrary.PortableSummary, val expectedFingerprint: String) : AutoCloseable {
        override fun close() { data.fill(0) }
    }
    private fun discard(value: Any?) {
        if (value is ByteArray) value.fill(0)
        if (value is AutoCloseable) value.close()
    }
    private fun export() {
        val password = phrase(true) ?: return
        runWork({ token ->
            try {
                authorize(token)
                val data = library.portableSnapshot()
                try { PortableBrain.encrypt(data, password).also { authorize(token) } } finally { data.fill(0) }
            } finally { password.fill('\u0000') }
        }) { archive ->
            encryptedExport?.fill(0); encryptedExport = archive
            startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE)
                .setType("application/octet-stream").putExtra(Intent.EXTRA_TITLE, "nexus-memoire.nxb"), 471)
        }
    }
    private fun previewImport() {
        val archive = encryptedImport?.copyOf() ?: return
        val password = phrase(false) ?: run { archive.fill(0); return }
        runWork({ token ->
            try {
                authorize(token)
                val plain = PortableBrain.decrypt(archive, password)
                try {
                    val counts = library.inspectPortable(plain)
                    val current = library.portableFingerprint()
                    authorize(token)
                    RestorePreview(plain, counts, current)
                }
                catch (failure: Exception) { plain.fill(0); throw failure }
            } finally { password.fill('\u0000'); archive.fill(0) }
        }) { candidate ->
            preview?.fill(0); preview = candidate.data
            expectedFingerprint = candidate.expectedFingerprint
            status.text = getString(R.string.backup_preview_counts, candidate.counts.activeNotes, candidate.counts.archivedNotes)
        }
    }
    private fun confirmRestore() {
        val plain = preview ?: return
        val expected = expectedFingerprint ?: return
        if (!foreground || !UserAccessSession.allowed() || busy) return
        dialog = AlertDialog.Builder(this).setTitle(R.string.backup_restore)
            .setMessage(R.string.backup_restore_confirm)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.backup_restore) { _, _ ->
                runWork({ token -> authorize(token); library.restorePortable(plain, expected) { authorize(token) }; true }) {
                    preview?.fill(0); preview = null; expectedFingerprint = null
                    status.setText(R.string.backup_restored)
                }
            }.create().also { it.window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE); it.show() }
    }
    @Deprecated("Android activity result")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 470) {
            status.setText(if (resultCode == RESULT_OK && UserAccessSession.allowed()) R.string.backup_unlocked else R.string.backup_locked)
            refresh(); return
        }
        val uri = data?.data
        if (requestCode == 471) {
            val archive = encryptedExport; encryptedExport = null
            if (archive == null) return
            if (resultCode != RESULT_OK || uri == null) { archive.fill(0); return }
            worker.execute {
                val okay = try {
                    contentResolver.openOutputStream(uri, "wt")?.use { it.write(archive) } ?: error("No output")
                    true
                } catch (_: Exception) { false } finally { archive.fill(0) }
                runOnUiThread { if (!destroyed) status.setText(if (okay) R.string.backup_exported else R.string.backup_failed) }
            }
        } else if (requestCode == 472 && resultCode == RESULT_OK && uri != null) {
            val token = epoch
            worker.execute {
                val archive = try { contentResolver.openInputStream(uri)?.use { KnowledgePayload.readBounded(it, PortableBrain.MAX_ARCHIVE_BYTES) } } catch (_: Exception) { null }
                runOnUiThread {
                    if (!destroyed && token == epoch) {
                        encryptedImport?.fill(0); encryptedImport = archive
                        status.setText(if (archive != null) R.string.backup_selected else R.string.backup_failed); refresh()
                    } else archive?.fill(0)
                }
            }
        }
    }
    private fun clearPrivatePreview() {
        secret.setText(""); confirmation.setText("")
        preview?.fill(0); preview = null; expectedFingerprint = null
    }
    override fun onResume() {
        super.onResume(); foreground = true; refresh()
        main.removeCallbacks(accessTick); main.postDelayed(accessTick, 1000)
        if (!UserAccessSession.allowed()) status.setText(R.string.backup_locked)
    }
    override fun onPause() {
        foreground = false; epoch++; UserAccessSession.lock()
        main.removeCallbacks(accessTick); wasUnlocked = false; clearPrivatePreview()
        dialog?.dismiss(); dialog = null; status.setText(R.string.backup_locked); refresh()
        super.onPause()
    }
    override fun onDestroy() {
        main.removeCallbacks(accessTick)
        destroyed = true; epoch++; encryptedImport?.fill(0); encryptedExport?.fill(0); preview?.fill(0)
        worker.shutdownNow(); super.onDestroy()
    }
}
