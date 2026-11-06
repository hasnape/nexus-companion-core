package fr.nexuscompanion.android

import android.app.AlertDialog
import android.content.pm.PackageManager
import android.os.Looper
import android.view.View
import android.view.ViewGroup
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
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import org.robolectric.shadows.ShadowAlertDialog
import org.robolectric.shadows.ShadowSpeechRecognizer
import org.robolectric.Shadows.shadowOf
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@LooperMode(LooperMode.Mode.PAUSED)
class MainActivityTest {
    private lateinit var lifecycle: ActivityController<MainActivity>
    private lateinit var activity: MainActivity
    @Before fun createActivity() {
        lifecycle = Robolectric.buildActivity(MainActivity::class.java).setup()
        activity = lifecycle.get()
    }
    @After fun destroyActivity() { lifecycle.pause().stop().destroy() }
    private fun views(view: View = activity.findViewById(android.R.id.content)): List<View> =
        listOf(view) + if (view is ViewGroup) (0 until view.childCount).flatMap { views(view.getChildAt(it)) } else emptyList()
    private fun input() = views().filterIsInstance<EditText>().single()
    private fun button(text: Int) = views().filterIsInstance<Button>().single { it.text.toString() == activity.getString(text) }
    private fun hasText(text: String) = views().filterIsInstance<TextView>().any { text in it.text.toString() }
    private fun await(condition: () -> Boolean) {
        val end = System.nanoTime() + 10_000_000_000L
        while (System.nanoTime() < end) {
            shadowOf(Looper.getMainLooper()).idle()
            if (condition()) return
            Thread.sleep(10)
        }
        fail("UI did not reach the expected state")
    }
    private fun send(text: String) { input().setText(text); button(R.string.send).performClick() }
    private fun libraryFile() = File(activity.filesDir, "knowledge-nexus-default.json")

    @Test fun typedConversationWorksWithoutMicrophoneOrInternetPermission() {
        assertEquals(PackageManager.PERMISSION_DENIED, activity.checkSelfPermission(android.Manifest.permission.RECORD_AUDIO))
        val requested = activity.packageManager.getPackageInfo(activity.packageName, PackageManager.GET_PERMISSIONS).requestedPermissions.orEmpty()
        assertFalse(requested.contains(android.Manifest.permission.INTERNET))
        send("Nexus, comment vas tu ?")
        await { hasText("Ça va, merci") }
        assertFalse(libraryFile().exists())
    }

    @Test fun directDictationStaysDisabledWithoutAnActiveMicrophone() {
        assertFalse(button(R.string.speak_now).isEnabled)
        send("bonjour")
        await { hasText("Bonjour ! Je t’écoute") }
        assertFalse(button(R.string.speak_now).isEnabled)
    }

    @Test @Config(sdk = [34]) fun voiceStatusSurvivesTextWorkAndPrivateDialogsClearThePreview() {
        shadowOf(activity.application).grantPermissions(android.Manifest.permission.RECORD_AUDIO)
        activity.onRequestPermissionsResult(410, arrayOf(android.Manifest.permission.RECORD_AUDIO), intArrayOf(PackageManager.PERMISSION_GRANTED))
        shadowOf(Looper.getMainLooper()).idle()
        val recognizer = shadowOf(ShadowSpeechRecognizer.getLatestSpeechRecognizer())
        recognizer.triggerOnReadyForSpeech(android.os.Bundle())
        assertTrue(hasText("Micro actif"))
        assertTrue(button(R.string.speak_now).isEnabled)
        recognizer.triggerOnPartialResults(android.os.Bundle().apply {
            putStringArrayList(android.speech.SpeechRecognizer.RESULTS_RECOGNITION, arrayListOf("phrase privée provisoire"))
        })
        assertTrue(hasText("En cours : phrase privée provisoire"))
        input().setText("Une note à relire")
        button(R.string.remember).performClick()
        assertFalse(hasText("phrase privée provisoire"))
        assertFalse(button(R.string.speak_now).isEnabled)
        ShadowAlertDialog.getLatestAlertDialog().getButton(AlertDialog.BUTTON_NEGATIVE).performClick()
        send("bonjour")
        await { hasText("Bonjour ! Je t’écoute") }
        assertFalse(hasText(activity.getString(R.string.local_ready)))
        assertTrue(hasText("Reprise de l'écoute locale") || hasText("Micro actif") || hasText("Préparation du micro"))
    }

    @Test fun aMemoryProposalIsNotSavedAndCanBeCanceled() {
        send("retiens que je préfère le thé")
        await { button(R.string.retain_proposal).visibility == View.VISIBLE }
        assertFalse(libraryFile().exists())
        button(R.string.retain_proposal).performClick()
        val dialog = ShadowAlertDialog.getLatestAlertDialog()
        assertTrue(dialog.findViewById<TextView>(android.R.id.message).text.toString().contains("thé"))
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE).performClick()
        assertFalse(libraryFile().exists())
        assertEquals(View.VISIBLE, button(R.string.retain_proposal).visibility)
    }

    @Test fun manualNoteSavingRequiresReviewAndCancelKeepsItUnsaved() {
        input().setText("Une note de test")
        button(R.string.remember).performClick()
        val dialog = ShadowAlertDialog.getLatestAlertDialog()
        assertTrue(dialog.findViewById<TextView>(android.R.id.message).text.contains("Une note de test"))
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE).performClick()
        assertFalse(libraryFile().exists())
    }

    @Test fun emptyNotesRemainAnHonestEmptyState() {
        button(R.string.my_notes).performClick()
        await { hasText(activity.getString(R.string.library_no_results)) }
        assertFalse(libraryFile().exists())
    }

    @Test fun damagedLibraryDoesNotBlockGreetingOrEraseTheOriginalFile() {
        val original = "unreadable library bytes".toByteArray()
        libraryFile().writeBytes(original)
        send("bonjour")
        await { hasText("Bonjour ! Je t’écoute") && hasText(activity.getString(R.string.library_read_failed)) }
        assertArrayEquals(original, libraryFile().readBytes())
    }

    @Test fun longInputIsBoundedBeforeItCanReachTheWorker() {
        input().setText("x".repeat(2500))
        assertEquals(2000, input().length())
    }

    @Test fun failedPrivateSaveIsNeverReportedAsSuccessfulOrOverwritten() {
        val original = "unreadable library bytes".toByteArray()
        libraryFile().writeBytes(original)
        input().setText("Une nouvelle note")
        button(R.string.remember).performClick()
        ShadowAlertDialog.getLatestAlertDialog().getButton(AlertDialog.BUTTON_POSITIVE).performClick()
        await { hasText(activity.getString(R.string.library_write_failed)) }
        assertFalse(hasText(activity.getString(R.string.note_saved)))
        assertArrayEquals(original, libraryFile().readBytes())
    }

    @Test fun activityRecreationDismissesPrivateDialogsWithoutSavingTheirContents() {
        input().setText("Une note personnelle non confirmée")
        button(R.string.remember).performClick()
        val dialog = ShadowAlertDialog.getLatestAlertDialog()
        assertTrue(dialog.isShowing)
        lifecycle.recreate()
        activity = lifecycle.get()
        assertFalse(dialog.isShowing)
        assertFalse(libraryFile().exists())
    }
}
