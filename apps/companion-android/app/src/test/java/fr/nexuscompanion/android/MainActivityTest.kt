package fr.nexuscompanion.android

import android.app.AlertDialog
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Rect
import android.media.AudioManager
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import org.robolectric.shadows.ShadowAlertDialog
import org.robolectric.shadows.ShadowSpeechRecognizer
import org.robolectric.shadows.ShadowTextToSpeech
import org.robolectric.Shadows.shadowOf
import java.io.File
import java.util.Locale

class MainActivityWithAndroidVoice : MainActivity() {
    override fun createVoiceInput(): VoiceInput = AndroidVoiceInput(applicationContext)
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@LooperMode(LooperMode.Mode.PAUSED)
class MainActivityTest {
    private lateinit var lifecycle: ActivityController<MainActivityWithAndroidVoice>
    private lateinit var activity: MainActivity
    @Before fun createActivity() {
        lifecycle = Robolectric.buildActivity(MainActivityWithAndroidVoice::class.java).setup()
        activity = lifecycle.get()
    }
    @After fun destroyActivity() { lifecycle.pause().stop().destroy() }
    private fun views(view: View = activity.findViewById(android.R.id.content)): List<View> =
        listOf(view) + if (view is ViewGroup) (0 until view.childCount).flatMap { views(view.getChildAt(it)) } else emptyList()
    private fun input() = views().filterIsInstance<EditText>().single()
    private fun spokenReplies() = views().filterIsInstance<Switch>().single()
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

    @Test @Config(sdk = [34]) fun typedConversationIsSpokenWithoutMicrophoneOrInternetPermission() {
        assertEquals(PackageManager.PERMISSION_DENIED, activity.checkSelfPermission(android.Manifest.permission.RECORD_AUDIO))
        val requested = activity.packageManager.getPackageInfo(activity.packageName, PackageManager.GET_PERMISSIONS).requestedPermissions.orEmpty()
        assertFalse(requested.contains(android.Manifest.permission.INTERNET))
        assertTrue(spokenReplies().isChecked)
        assertEquals(AudioManager.STREAM_MUSIC, activity.volumeControlStream)
        ShadowTextToSpeech.addVoice(Voice("french-local", Locale.FRANCE, Voice.QUALITY_NORMAL,
            Voice.LATENCY_NORMAL, false, emptySet()))
        shadowOf(activity.getSystemService(AudioManager::class.java))
            .setNextFocusRequestResponse(AudioManager.AUDIOFOCUS_REQUEST_GRANTED)

        send("Nexus, comment vas tu ?")
        await { hasText("Ça va, merci") && ShadowTextToSpeech.getLastTextToSpeechInstance() != null }
        val speech = shadowOf(ShadowTextToSpeech.getLastTextToSpeechInstance())
        speech.onInitListener.onInit(TextToSpeech.SUCCESS)
        await { speech.lastSpokenText?.contains("Ça va, merci") == true }
        assertFalse(libraryFile().exists())
        assertNull(ShadowSpeechRecognizer.getLatestSpeechRecognizer())
    }

    @Test fun aPreviouslySilentInstallationCanEnableSpokenRepliesIndependently() {
        activity.getSharedPreferences("nexus_voice_preferences", Context.MODE_PRIVATE).edit()
            .putBoolean("auto_listen_enabled", false).remove("spoken_replies_enabled").apply()
        lifecycle.recreate()
        activity = lifecycle.get()
        assertFalse(spokenReplies().isChecked)
        send("bonjour")
        await { hasText("Bonjour ! Je t’écoute") }
        assertNull(ShadowTextToSpeech.getLastTextToSpeechInstance())

        spokenReplies().performClick()
        lifecycle.recreate()
        activity = lifecycle.get()
        assertTrue(spokenReplies().isChecked)
        assertFalse(button(R.string.speak_now).isEnabled)
    }

    @Test @Config(sdk = [34]) fun writtenReplyIsSpokenWhenTheRecognitionServiceIsUnavailable() {
        shadowOf(activity.application).grantPermissions(android.Manifest.permission.RECORD_AUDIO)
        ShadowSpeechRecognizer.setIsOnDeviceRecognitionAvailable(false)
        activity.onRequestPermissionsResult(410, arrayOf(android.Manifest.permission.RECORD_AUDIO), intArrayOf(PackageManager.PERMISSION_GRANTED))
        ShadowTextToSpeech.addVoice(Voice("french-local", Locale.FRANCE, Voice.QUALITY_NORMAL,
            Voice.LATENCY_NORMAL, false, emptySet()))
        shadowOf(activity.getSystemService(AudioManager::class.java))
            .setNextFocusRequestResponse(AudioManager.AUDIOFOCUS_REQUEST_GRANTED)

        send("bonjour")
        await { hasText("Bonjour ! Je t’écoute") && ShadowTextToSpeech.getLastTextToSpeechInstance() != null }
        val speech = shadowOf(ShadowTextToSpeech.getLastTextToSpeechInstance())
        speech.onInitListener.onInit(TextToSpeech.SUCCESS)
        await { speech.lastSpokenText?.contains("Bonjour ! Je t’écoute") == true }
        assertFalse(button(R.string.speak_now).isEnabled)
        assertNull(ShadowSpeechRecognizer.getLatestSpeechRecognizer())
    }

    @Test fun directDictationStaysDisabledWithoutAnActiveMicrophone() {
        assertFalse(button(R.string.speak_now).isEnabled)
        send("bonjour")
        await { hasText("Bonjour ! Je t’écoute") }
        assertFalse(button(R.string.speak_now).isEnabled)
    }

    @Test fun updatesOpenThePrivateNativeScreenWithoutChangingNotes() {
        val notes = File(activity.filesDir, "knowledge-nexus-default.json").apply { writeText("private bytes") }
        button(R.string.updates).performClick()
        val intent = shadowOf(activity).nextStartedActivity
        assertEquals(UpdateActivity::class.java.name, intent.component?.className)
        assertEquals("private bytes", notes.readText())
    }

    @Test fun voiceSetupIsPrivateAndTheTestChannelClearlyIdentifiesItsSeparateMemory() {
        assertEquals(BuildConfig.IS_PREVIEW, hasText(activity.getString(R.string.preview_warning)))
        button(R.string.voice_settings).performClick()
        assertEquals(VoiceSettingsActivity::class.java.name, shadowOf(activity).nextStartedActivity.component?.className)
        assertFalse(activity.packageManager.getActivityInfo(android.content.ComponentName(activity, VoiceSettingsActivity::class.java), 0).exported)
    }

    @Test @Config(sdk = [34]) fun voiceStatusSurvivesTextWorkAndPrivateDialogsClearThePreview() {
        shadowOf(activity.application).grantPermissions(android.Manifest.permission.RECORD_AUDIO)
        activity.onRequestPermissionsResult(410, arrayOf(android.Manifest.permission.RECORD_AUDIO), intArrayOf(PackageManager.PERMISSION_GRANTED))
        shadowOf(Looper.getMainLooper()).idle()
        val recognizer = shadowOf(ShadowSpeechRecognizer.getLatestSpeechRecognizer())
        val avatar = activity.findViewById<NexusAvatarView>(R.id.nexus_avatar)
        assertEquals(CompanionPresence.READY, avatar.presence)
        recognizer.triggerOnReadyForSpeech(android.os.Bundle())
        assertEquals(CompanionPresence.LISTENING, avatar.presence)
        assertTrue(hasText(activity.getString(R.string.home_presence_listening)))
        assertTrue(hasText("Micro actif"))
        assertTrue(button(R.string.speak_now).isEnabled)
        recognizer.triggerOnPartialResults(android.os.Bundle().apply {
            putStringArrayList(android.speech.SpeechRecognizer.RESULTS_RECOGNITION, arrayListOf("phrase privée provisoire"))
        })
        assertTrue(hasText("En cours : phrase privée provisoire"))
        input().setText("Une note à relire")
        button(R.string.remember).performClick()
        assertEquals(CompanionPresence.READY, avatar.presence)
        assertFalse(hasText("phrase privée provisoire"))
        assertFalse(button(R.string.speak_now).isEnabled)
        val previousSpeech = ShadowTextToSpeech.getLastTextToSpeechInstance()
        ShadowAlertDialog.getLatestAlertDialog().getButton(AlertDialog.BUTTON_NEGATIVE).performClick()
        shadowOf(Looper.getMainLooper()).idle()
        val resumedSpeech = ShadowTextToSpeech.getLastTextToSpeechInstance()
        assertNotSame(previousSpeech, resumedSpeech)
        shadowOf(resumedSpeech).onInitListener.onInit(TextToSpeech.ERROR)
        shadowOf(Looper.getMainLooper()).idle()
        send("bonjour")
        await { hasText("Bonjour ! Je t’écoute") }
        assertFalse(hasText(activity.getString(R.string.local_ready)))
        assertTrue(hasText("Reprise de l'écoute locale") || hasText("Micro actif") || hasText("Préparation du micro"))
        assertTrue(hasText(activity.getString(R.string.voice_output_unavailable)))
    }

    @Test @Config(qualifiers = "w320dp-h640dp-mdpi") fun homeControlsFitOnANarrowScreenWithLargerText() {
        RuntimeEnvironment.setFontScale(1.5f)
        lifecycle.recreate()
        activity = lifecycle.get()
        assertEquals(1.5f, activity.resources.configuration.fontScale, 0.01f)
        val scroll = activity.findViewById<ScrollView>(R.id.home_scroll)
        scroll.measure(
            View.MeasureSpec.makeMeasureSpec(320, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(640, View.MeasureSpec.EXACTLY)
        )
        scroll.layout(0, 0, 320, 640)

        val controlIds = listOf(R.id.home_microphone, R.id.home_spoken_replies, R.id.home_speak_now,
            R.id.home_input, R.id.home_send, R.id.home_notes, R.id.home_remember, R.id.home_updates)
        for (id in controlIds) {
            val control = activity.findViewById<TextView>(id)
            val name = activity.resources.getResourceEntryName(id)
            assertTrue(name, control.height >= 48)
            val bounds = Rect(0, 0, control.width, control.height)
            scroll.offsetDescendantRectToMyCoords(control, bounds)
            assertTrue(name, bounds.left >= 0 && bounds.right <= scroll.width)
            val label = if (control is EditText) control.hint else control.text
            assertFalse(name, label.isNullOrBlank())
        }
        assertTrue(scroll.canScrollVertically(1))
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
