package fr.nexuscompanion.android

import android.Manifest
import android.content.ClipboardManager
import android.media.AudioManager
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import android.view.View
import android.view.ViewGroup
import android.widget.Button
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
import org.robolectric.shadows.ShadowSpeechRecognizer
import org.robolectric.shadows.ShadowTextToSpeech
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@LooperMode(LooperMode.Mode.PAUSED)
class VoiceSettingsActivityTest {
    private lateinit var lifecycle: ActivityController<VoiceSettingsActivity>
    private lateinit var activity: VoiceSettingsActivity
    @Before fun open() {
        shadowOf(RuntimeEnvironment.getApplication()).grantPermissions(Manifest.permission.RECORD_AUDIO)
        lifecycle = Robolectric.buildActivity(VoiceSettingsActivity::class.java).setup(); activity = lifecycle.get()
        shadowOf(Looper.getMainLooper()).idle()
    }
    @After fun close() { lifecycle.pause().stop().destroy() }
    private fun views(view: View = activity.findViewById(android.R.id.content)): List<View> =
        listOf(view) + if (view is ViewGroup) (0 until view.childCount).flatMap { views(view.getChildAt(it)) } else emptyList()
    private fun button(id: Int) = views().filterIsInstance<Button>().single { it.text == activity.getString(id) }
    @Test fun openingThePanelShowsThePackagedFrenchModelWithoutStartingRecognition() {
        assertTrue(views().filterIsInstance<TextView>().any { it.text == activity.getString(R.string.voice_model_embedded) })
        assertNull(ShadowSpeechRecognizer.getLatestSpeechRecognizer())
    }
    @Test fun diagnosticCopyContainsOnlyVersionAndPublicSignatureMetadata() {
        button(R.string.voice_copy_diagnostic).performClick()
        val text = activity.getSystemService(ClipboardManager::class.java).primaryClip!!.getItemAt(0).text.toString()
        assertTrue(text.contains("Android API")); assertTrue(text.contains("Signature"))
        assertFalse(text.contains("Entendu :"))
        assertNull(ShadowSpeechRecognizer.getLatestSpeechRecognizer())
    }

    @Test fun localVoiceTestPlaysTheSampleWithoutCapturingAudio() {
        shadowOf(RuntimeEnvironment.getApplication()).denyPermissions(Manifest.permission.RECORD_AUDIO)
        ShadowTextToSpeech.addVoice(Voice("french-online", Locale.FRANCE, Voice.QUALITY_VERY_HIGH,
            Voice.LATENCY_NORMAL, true, emptySet()))
        ShadowTextToSpeech.addVoice(Voice("french-local", Locale.FRANCE, Voice.QUALITY_NORMAL,
            Voice.LATENCY_NORMAL, false, emptySet()))
        shadowOf(activity.getSystemService(AudioManager::class.java))
            .setNextFocusRequestResponse(AudioManager.AUDIOFOCUS_REQUEST_GRANTED)

        button(R.string.voice_output_test).performClick()
        val speech = shadowOf(ShadowTextToSpeech.getLastTextToSpeechInstance())
        speech.onInitListener.onInit(TextToSpeech.SUCCESS)
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(activity.getString(R.string.voice_output_sample), speech.lastSpokenText)
        assertEquals("french-local", speech.currentVoice.name)
        assertNull(ShadowSpeechRecognizer.getLatestSpeechRecognizer())
        assertTrue(button(R.string.voice_output_test).isEnabled)
    }
}
