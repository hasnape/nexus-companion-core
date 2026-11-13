package fr.nexuscompanion.android

import android.app.Activity
import android.app.KeyguardManager
import android.content.Intent
import android.os.Bundle
import android.text.InputFilter
import android.text.InputType
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import java.util.concurrent.Executors

/** Codes are entered here only, never through the microphone, conversation or clipboard reader. */
open class UserCodeActivity : Activity() {
    private lateinit var store: UserCodeStore
    private lateinit var content: LinearLayout
    private lateinit var status: TextView
    private val worker = Executors.newSingleThreadExecutor()
    private var setupAuthorized = false
    private var checkingDevice = false
    private var busy = false
    internal open fun createCodeStore() = UserCodeStore(applicationContext)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        store = createCodeStore()
        render()
    }
    private fun render() {
        content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(40, 48, 40, 24) }
        setContentView(android.widget.ScrollView(this).apply {
            addView(content)
            setOnApplyWindowInsetsListener { view, insets ->
                val bars = insets.getInsets(android.view.WindowInsets.Type.systemBars() or android.view.WindowInsets.Type.ime())
                view.setPadding(bars.left, bars.top, bars.right, bars.bottom); insets
            }
            requestApplyInsets()
        })
        status = TextView(this).apply { textSize = 20f; setText(R.string.code_intro) }
        content.addView(status)
        val configured = try { store.configured() } catch (_: Exception) {
            status.setText(R.string.code_unreadable); true
        }
        if (!configured && !setupAuthorized) {
            button(R.string.code_create) { authorizeSetup() }
        } else if (setupAuthorized) {
            status.append(getString(R.string.code_choose))
            val first = field(R.string.code_new); val second = field(R.string.code_confirm)
            button(R.string.code_save) {
                if (busy) return@button
                val code = first.text.toString().toCharArray(); val confirmation = second.text.toString().toCharArray()
                val valid = code.size in 6..12 && code.all { it in '0'..'9' } && code.contentEquals(confirmation)
                confirmation.fill('\u0000'); first.setText(""); second.setText("")
                if (!valid) { code.fill('\u0000'); status.setText(R.string.code_mismatch); return@button }
                busy = true
                worker.execute {
                    val succeeded = try { store.set(code); true } catch (_: Exception) { false } finally { code.fill('\u0000') }
                    runOnUiThread { busy = false; if (succeeded) grant() else status.setText(R.string.code_save_failed) }
                }
            }
        } else {
            val codeField = field(R.string.code_user)
            button(R.string.code_unlock) {
                if (busy) return@button
                val code = codeField.text.toString().toCharArray(); codeField.setText(""); busy = true
                worker.execute {
                    val result = try { store.verify(code) } catch (_: Exception) { null } finally { code.fill('\u0000') }
                    runOnUiThread {
                        busy = false
                        if (result == UserCodeStore.Result.ACCEPTED) grant()
                        else status.setText(when (result) {
                            UserCodeStore.Result.WAIT -> R.string.code_wait
                            UserCodeStore.Result.DEVICE_RECOVERY_REQUIRED -> R.string.code_recovery_required
                            null -> R.string.code_verify_failed
                            else -> R.string.code_wrong
                        })
                    }
                }
            }
            button(R.string.code_change) { authorizeSetup() }
        }
        button(R.string.cancel) { setResult(RESULT_CANCELED); finish() }
    }
    private fun field(hintText: Int) = EditText(this).apply {
        setHint(hintText); inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
        filters = arrayOf(InputFilter.LengthFilter(12)); setSingleLine(true); isSaveEnabled = false
        importantForAutofill = android.view.View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
        content.addView(this)
    }
    private fun button(text: Int, action: () -> Unit) = Button(this).apply { setText(text); setOnClickListener { action() }; content.addView(this) }
    @Suppress("DEPRECATION")
    private fun authorizeSetup() {
        if (busy || checkingDevice) return
        val manager = getSystemService(KeyguardManager::class.java)
        if (!manager.isDeviceSecure) {
            // Recovery never falls back to a creator code or an unprotected replacement.
            if (try { store.configured() } catch (_: Exception) { true }) {
                status.setText(R.string.code_android_setup); return
            }
            setupAuthorized = true; render(); return
        }
        val intent = manager.createConfirmDeviceCredentialIntent(getString(R.string.app_name), getString(R.string.code_android_confirm))
        if (intent == null) { status.setText(R.string.code_android_unavailable); return }
        checkingDevice = true
        try { startActivityForResult(intent, 423) } catch (_: RuntimeException) { checkingDevice = false; status.setText(R.string.code_android_failed) }
    }
    @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 423) {
            checkingDevice = false
            if (resultCode == RESULT_OK) { setupAuthorized = true; render() }
        }
    }
    private fun grant() {
        if (isFinishing || isDestroyed || !hasWindowFocus()) return
        UserAccessSession.unlock(); setResult(RESULT_OK); finish()
    }
    override fun onPause() {
        if (!checkingDevice && !isFinishing) { setupAuthorized = false; finish() }
        super.onPause()
    }
    override fun onDestroy() { worker.shutdown(); super.onDestroy() }
}
