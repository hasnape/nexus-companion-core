package fr.nexuscompanion.android

import android.Manifest
import android.os.Bundle
import android.os.Looper
import android.speech.RecognitionSupport
import android.speech.SpeechRecognizer
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import org.robolectric.shadows.ShadowSpeechRecognizer
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@LooperMode(LooperMode.Mode.PAUSED)
class FrenchVoiceSupportTest {
    private val context get() = RuntimeEnvironment.getApplication()
    @Before fun permission() { shadowOf(context).grantPermissions(Manifest.permission.RECORD_AUDIO) }
    private fun latest() = shadowOf(ShadowSpeechRecognizer.getLatestSpeechRecognizer())
    @Test @Config(sdk = [31]) fun android12NeverCallsUnavailableLanguageSupportApis() {
        val states = mutableListOf<FrenchModelState>(); val checker = FrenchVoiceSupport(context, states::add)
        try {
            checker.check()
            assertTrue(states.last() in listOf(FrenchModelState.UNKNOWN, FrenchModelState.SERVICE_ABSENT))
            assertFalse(states.contains(FrenchModelState.READY))
        } finally { checker.close() }
    }
    @Test fun frenchVariantPolicyDoesNotTreatAnotherLanguageAsReady() {
        assertEquals(FrenchModelState.READY, FrenchVoicePolicy.state(listOf("fr_FR"), emptyList(), emptyList()))
        assertEquals(FrenchModelState.READY, FrenchVoicePolicy.state(listOf("fr-Latn-FR"), emptyList(), emptyList()))
        assertEquals(FrenchModelState.DOWNLOADABLE, FrenchVoicePolicy.state(listOf("en-US", "fr-CA"), emptyList(), listOf("fr")))
        assertEquals(FrenchModelState.PENDING, FrenchVoicePolicy.state(emptyList(), listOf("fr-FR"), listOf("fr")))
        assertEquals(FrenchModelState.UNAVAILABLE, FrenchVoicePolicy.state(listOf("en-US"), emptyList(), listOf("en-US")))
    }
    @Test fun installedModelIsConfirmedWithoutStartingAudioOrDownloading() {
        val states = mutableListOf<FrenchModelState>(); val checker = FrenchVoiceSupport(context, states::add)
        try {
            checker.check(); shadowOf(Looper.getMainLooper()).idle()
            val engine = latest()
            assertNull(engine.lastRecognizerIntent)
            assertNull(engine.latestModelDownloadIntent)
            engine.triggerSupportResult(RecognitionSupport.Builder().setInstalledOnDeviceLanguages(listOf("fr-FR")).build())
            shadowOf(Looper.getMainLooper()).idle()
            assertEquals(FrenchModelState.READY, states.last())
            assertTrue(engine.isDestroyed)
        } finally { checker.close() }
    }
    @Test fun modelDownloadRequiresAnExplicitActionAfterSupportedMissingStatus() {
        val states = mutableListOf<FrenchModelState>(); val checker = FrenchVoiceSupport(context, states::add)
        try {
            checker.requestDownload(); assertNull(ShadowSpeechRecognizer.getLatestSpeechRecognizer())
            checker.check(); shadowOf(Looper.getMainLooper()).idle()
            latest().triggerSupportResult(RecognitionSupport.Builder().setSupportedOnDeviceLanguages(listOf("fr-FR")).build())
            shadowOf(Looper.getMainLooper()).idle()
            assertEquals(FrenchModelState.DOWNLOADABLE, states.last())
            assertNull(latest().latestModelDownloadIntent)
            checker.requestDownload(); shadowOf(Looper.getMainLooper()).idle()
            assertEquals("fr-FR", latest().latestModelDownloadIntent?.getStringExtra("android.speech.extra.LANGUAGE"))
            latest().triggerSupportResult(RecognitionSupport.Builder().setPendingOnDeviceLanguages(listOf("fr-FR")).build())
            shadowOf(Looper.getMainLooper()).idle()
            assertEquals(FrenchModelState.PENDING, states.last())
        } finally { checker.close() }
    }
    @Test fun unsupportedChecksAndTimeoutsNeverClaimTheModelIsInstalled() {
        val states = mutableListOf<FrenchModelState>(); val checker = FrenchVoiceSupport(context, states::add)
        try {
            checker.check(); shadowOf(Looper.getMainLooper()).idle()
            latest().triggerSupportError(SpeechRecognizer.ERROR_CANNOT_CHECK_SUPPORT)
            shadowOf(Looper.getMainLooper()).idle()
            assertEquals(FrenchModelState.UNKNOWN, states.last())
            checker.check(); shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(7))
            assertEquals(FrenchModelState.UNKNOWN, states.last())
            assertFalse(states.contains(FrenchModelState.READY))
        } finally { checker.close() }
    }
    @Test fun closingInvalidatesLateLanguageCallbacks() {
        val states = mutableListOf<FrenchModelState>(); val checker = FrenchVoiceSupport(context, states::add)
        checker.check(); shadowOf(Looper.getMainLooper()).idle()
        val old = latest(); checker.close()
        old.triggerSupportResult(RecognitionSupport.Builder().setInstalledOnDeviceLanguages(listOf("fr")).build())
        shadowOf(Looper.getMainLooper()).idle()
        assertFalse(states.contains(FrenchModelState.READY))
    }
    @Test fun unavailableServiceAndMissingPermissionDoNotCreateARecognizer() {
        val states = mutableListOf<FrenchModelState>(); val checker = FrenchVoiceSupport(context, states::add)
        ShadowSpeechRecognizer.setIsOnDeviceRecognitionAvailable(false)
        checker.check(); assertEquals(FrenchModelState.SERVICE_ABSENT, states.last())
        ShadowSpeechRecognizer.setIsOnDeviceRecognitionAvailable(true)
        shadowOf(context).denyPermissions(Manifest.permission.RECORD_AUDIO)
        checker.check(); assertEquals(FrenchModelState.PERMISSION, states.last())
        assertNull(ShadowSpeechRecognizer.getLatestSpeechRecognizer())
    }
}
