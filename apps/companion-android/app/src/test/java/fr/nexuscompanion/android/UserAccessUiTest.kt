package fr.nexuscompanion.android

import android.app.Activity
import android.app.KeyguardManager
import android.content.Context
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import java.io.File

class PrivateMainActivity : MainActivityWithAndroidVoice() {
    override fun privateAccessAllowed() = UserAccessSession.allowed()
}
class TestUserCodeActivity : UserCodeActivity() {
    override fun createCodeStore() = UserCodeStore(applicationContext, LocalKnowledgeLibraryTest.JvmCipher("ui-code"))
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@LooperMode(LooperMode.Mode.PAUSED)
class UserAccessUiTest {
    private val activities = mutableListOf<ActivityController<out Activity>>()
    @Before fun startLocked() { UserAccessSession.lock() }
    @After fun destroy() {
        activities.asReversed().forEach { if (!it.get().isDestroyed) it.pause().stop().destroy() }
        UserAccessSession.lock(); ChromeAiReaderService.cancelReading()
    }
    private fun views(view: View): List<View> = listOf(view) + if (view is ViewGroup)
        (0 until view.childCount).flatMap { views(view.getChildAt(it)) } else emptyList()
    private fun all(activity: Activity) = views(activity.findViewById(android.R.id.content))
    private fun click(activity: Activity, text: String) = all(activity).filterIsInstance<Button>().single { it.text.toString() == text }.performClick()
    private fun await(check: () -> Boolean) {
        val end = System.nanoTime() + 15_000_000_000L
        while (System.nanoTime() < end) {
            shadowOf(Looper.getMainLooper()).idle()
            if (check()) return
            Thread.sleep(10)
        }
        fail("Expected secure UI state was not reached")
    }
    private fun main(): ActivityController<PrivateMainActivity> = Robolectric.buildActivity(PrivateMainActivity::class.java)
        .setup().also { activities.add(it) }
    private fun send(activity: MainActivity, text: String) {
        activity.findViewById<EditText>(R.id.home_input).setText(text)
        activity.findViewById<Button>(R.id.home_send).performClick()
    }
    private fun body(activity: MainActivity) = activity.findViewById<TextView>(R.id.home_answer).text.toString()
    private fun code(): ActivityController<TestUserCodeActivity> = Robolectric.buildActivity(TestUserCodeActivity::class.java)
        .setup().visible().windowFocusChanged(true).also { activities.add(it) }

    @Test fun firstSetupHasNoDefaultCodeAndRequiresMatchingSixDigitValues() {
        val context = RuntimeEnvironment.getApplication()
        File(context.filesDir, "user-code.v1").delete()
        val lifecycle = code(); val activity = lifecycle.get()
        assertEquals(WindowManager.LayoutParams.FLAG_SECURE, activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE)
        click(activity, "Créer mon code utilisateur")
        val fields = all(activity).filterIsInstance<EditText>()
        assertEquals(2, fields.size)
        fields[0].setText("123456"); fields[1].setText("123457")
        click(activity, "Enregistrer le code")
        assertFalse(UserAccessSession.allowed())
        fields[0].setText("739125"); fields[1].setText("739125")
        click(activity, "Enregistrer le code")
        await { activity.isFinishing }
        assertEquals(Activity.RESULT_OK, shadowOf(activity).resultCode)
        assertTrue(UserAccessSession.allowed())
        assertTrue(fields.all { it.text.isEmpty() })
    }
    @Test fun wrongCodeCannotGrantAccessAndRecoveryRequiresAndroidLock() {
        val context = RuntimeEnvironment.getApplication()
        UserCodeStore(context, LocalKnowledgeLibraryTest.JvmCipher("ui-code")).set("739125".toCharArray())
        val activity = code().get()
        all(activity).filterIsInstance<EditText>().single().setText("000000")
        click(activity, "Déverrouiller")
        await { all(activity).filterIsInstance<TextView>().any { it.text.toString() == "Code incorrect." } }
        assertFalse(UserAccessSession.allowed())
        shadowOf(activity.getSystemService(KeyguardManager::class.java)).setIsDeviceSecure(false)
        click(activity, "Changer ou récupérer mon code")
        assertFalse(UserAccessSession.allowed())
        assertEquals(1, all(activity).filterIsInstance<EditText>().size)
        assertTrue(all(activity).filterIsInstance<TextView>().any { it.text.contains("verrouillage Android") })
    }
    @Test fun correctCodeOpensOnlyAnEphemeralSession() {
        val context = RuntimeEnvironment.getApplication()
        UserCodeStore(context, LocalKnowledgeLibraryTest.JvmCipher("ui-code")).set("739125".toCharArray())
        val activity = code().get()
        all(activity).filterIsInstance<EditText>().single().setText("739125")
        click(activity, "Déverrouiller")
        await { activity.isFinishing }
        assertTrue(UserAccessSession.allowed()); assertEquals(Activity.RESULT_OK, shadowOf(activity).resultCode)
        assertTrue(all(activity).filterIsInstance<EditText>().single().text.isEmpty())
    }
    @Test fun privateQuestionAndNotesOpenTheSecureActivityWithoutRevealingData() {
        val activity = main().get()
        activity.createKnowledgeLibrary().addUserNote("Métier secret", "Je travaille comme technicien informatique.")
        send(activity, "Quel est mon métier ?")
        await { body(activity).contains("code utilisateur") }
        assertFalse(body(activity).contains("technicien"))
        var request = shadowOf(activity).nextStartedActivityForResult
        while (request != null && request.requestCode != 423) request = shadowOf(activity).nextStartedActivityForResult
        assertNotNull(request)
        assertEquals(UserCodeActivity::class.java.name, request!!.intent.component!!.className)
        assertTrue(request.intent.extras == null || request.intent.extras!!.isEmpty)
    }
    @Test fun leavingTheAppWipesAFullPersonalReplyAndLocksTheSession() {
        UserAccessSession.unlock()
        val lifecycle = main(); val activity = lifecycle.get()
        activity.createKnowledgeLibrary().addUserNote("Métier", "Je travaille comme technicien informatique.")
        send(activity, "Quel est mon métier ?")
        await { body(activity).contains("technicien") }
        assertTrue(activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0)
        lifecycle.pause()
        assertFalse(UserAccessSession.allowed()); assertFalse(body(activity).contains("technicien"))
        lifecycle.resume()
        send(activity, "répète")
        await { body(activity).contains("réponse récente") }
        assertFalse(body(activity).contains("technicien"))
    }
    @Test fun successfulSecureActivityReturnResumesTheOriginalQuestionOnlyOnce() {
        val lifecycle = main(); val activity = lifecycle.get()
        activity.createKnowledgeLibrary().addUserNote("Métier", "Je travaille comme technicien informatique.")
        send(activity, "Quel est mon métier ?")
        await { body(activity).contains("code utilisateur") }
        var request = shadowOf(activity).nextStartedActivityForResult
        while (request != null && request.requestCode != 423) request = shadowOf(activity).nextStartedActivityForResult
        val auth = requireNotNull(request)
        lifecycle.pause()
        UserAccessSession.unlock()
        shadowOf(activity).receiveResult(auth.intent, Activity.RESULT_OK, null)
        lifecycle.resume()
        await { body(activity).contains("technicien informatique") }
        assertTrue(UserAccessSession.allowed())
        assertTrue(activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0)
    }
    @Test fun relockingImmediatelyClearsAnyPrivateReplyAndItsSource() {
        UserAccessSession.unlock()
        val activity = main().get()
        activity.createKnowledgeLibrary().addUserNote("Identité", "Je m’appelle Amine HARBI.")
        send(activity, "Quel est mon nom complet ?")
        await { body(activity).contains("HARBI") }
        activity.findViewById<Button>(R.id.home_lock_access).performClick()
        assertFalse(UserAccessSession.allowed()); assertFalse(body(activity).contains("HARBI"))
        assertEquals(View.GONE, activity.findViewById<TextView>(R.id.home_answer_source).visibility)
    }
}
