package fr.nexuscompanion.android

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import java.util.concurrent.Executors

/** Read-only, user-requested assistive reading, restricted to Google Search in stable Chrome. */
class ChromeAiReaderService : AccessibilityService() {
    private val main = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor()
    private var running = false
    private var lastText: String? = null
    private var lastSource: String? = null
    private var lastRequest = 0L
    private val probe = Runnable { inspectRequestedPage() }

    override fun onServiceConnected() { connected = this }

    // Idle events never read a window. Only request() starts the finite polling loop.
    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() { cancel() }

    override fun onDestroy() {
        cancel()
        if (connected === this) connected = null
        worker.shutdownNow()
        super.onDestroy()
    }

    private fun schedule() {
        main.removeCallbacks(probe)
        main.postDelayed(probe, 700)
    }

    private fun request(expectedQuery: String?): Boolean {
        session.begin(expectedQuery)
        lastRequest = 0L
        lastText = null
        lastSource = null
        schedule()
        return true
    }

    private fun cancel() {
        main.removeCallbacks(probe)
        session.clear()
        lastText = null
        lastSource = null
    }

    private fun inspectRequestedPage() {
        val request = session.pending() ?: return
        if (running) { schedule(); return }
        running = true
        worker.execute {
            var chromeVisible = false
            val reading = try {
                val root = if (session.current(request.id) == null) null else rootInActiveWindow
                if (root == null) null else {
                    try {
                        chromeVisible = root.packageName?.toString() == CHROME
                        if (!chromeVisible || SystemClock.elapsedRealtime() >= request.expires) null
                        else snapshot(root, request.expires) { session.current(request.id) != null }
                            ?.let { ChromeAiText.extract(it, request.expectedQuery) }
                    } finally { @Suppress("DEPRECATION") root.recycle() }
                }
            } catch (_: RuntimeException) { null }
            main.post {
                running = false
                if (connected !== this) return@post
                if (session.current(request.id) == null) {
                    if (session.active() != null) schedule()
                    return@post
                }
                val expired = SystemClock.elapsedRealtime() >= request.expires
                val stable = reading != null && lastRequest == request.id && lastText == reading.text && lastSource == reading.source
                lastRequest = request.id
                lastText = reading?.text
                lastSource = reading?.source
                if (stable || expired) {
                    if (session.complete(request.id, if (stable && !expired) reading else null) && chromeVisible) returnToCompanion()
                } else schedule()
            }
        }
    }

    private fun returnToCompanion() {
        try {
            startActivity(Intent(this, MainActivity::class.java).addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            ))
        } catch (_: RuntimeException) { /* The result remains available for an explicit return to Nexus. */ }
    }

    /** Chrome toolbar origin first, then only visible non-editable document text. Never perform an action. */
    internal fun snapshot(root: AccessibilityNodeInfo, deadline: Long, stillRequested: () -> Boolean = { true }): ChromeAiText.Snapshot? {
        if (root.packageName?.toString() != CHROME) return null
        var address: String? = null
        val blocks = mutableListOf<ChromeAiText.Block>()
        var nodes = 0
        var chars = 0
        var overBudget = false
        fun visit(node: AccessibilityNodeInfo, inDocument: Boolean, depth: Int) {
            if (!stillRequested() || ++nodes > 800 || depth > 40 || SystemClock.elapsedRealtime() >= deadline) { overBudget = true; return }
            val document = inDocument || node.className?.toString() == "android.webkit.WebView"
            if (document && (node.isPassword || node.isEditable)) return
            if (!document && node.viewIdResourceName == "$CHROME:id/url_bar" && !node.isFocused && node.isVisibleToUser) {
                address = node.text?.toString()?.takeIf { it.length <= 2048 }
            }
            if (document && node.isVisibleToUser && !node.isPassword && !node.isEditable) {
                val text = node.text?.toString()?.takeIf { it.isNotBlank() }
                if (text != null) {
                    chars += text.length
                    if (chars > ChromeAiText.MAX_CAPTURE_CHARS || blocks.size >= ChromeAiText.MAX_BLOCKS) { overBudget = true; return }
                    val control = node.className?.toString() in setOf("android.widget.Button", "android.widget.EditText")
                    blocks.add(ChromeAiText.Block(text, node.isHeading, control))
                }
            }
            for (index in 0 until minOf(node.childCount, 100)) {
                if (overBudget) break
                val child = node.getChild(index) ?: continue
                try { visit(child, document, depth + 1) } finally { @Suppress("DEPRECATION") child.recycle() }
            }
        }
        visit(root, false, 0)
        return if (overBudget) null else ChromeAiText.Snapshot(address, blocks)
    }

    companion object {
        internal const val CHROME = "com.android.chrome"
        internal val session = ChromeReadingSession(SystemClock::elapsedRealtime)
        private var connected: ChromeAiReaderService? = null
        internal fun available(): Boolean = connected != null
        internal fun begin(expectedQuery: String?): Boolean = connected?.request(expectedQuery) == true
        internal fun cancelReading() { connected?.cancel(); session.clear() }
        internal fun settingsIntent(context: android.content.Context): Intent = Intent("android.settings.ACCESSIBILITY_DETAILS_SETTINGS")
            .putExtra(Intent.EXTRA_COMPONENT_NAME, ComponentName(context, ChromeAiReaderService::class.java).flattenToString())
        internal fun fallbackSettingsIntent(): Intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
    }
}
