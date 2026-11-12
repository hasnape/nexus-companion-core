package fr.nexuscompanion.android

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.ClipData
import android.content.ClipboardManager
import android.content.pm.PackageManager
import android.graphics.Rect
import android.media.AudioManager
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsetsController
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
    var chromeReady = false
    var chromeLaunches = 0
    override fun beginChromeReading(expectedQuery: String?): Boolean {
        if (!chromeReady) return false
        ChromeAiReaderService.session.begin(expectedQuery)
        return true
    }
    override fun launchChromeForReading(): Boolean { chromeLaunches++; return chromeReady }
    var externalResult: ExternalAppPolicy.LaunchResult? = null
    val externalRequests = mutableListOf<ExternalAppPolicy.Request>()
    override fun launchExternalApp(request: ExternalAppPolicy.Request): ExternalAppPolicy.LaunchResult {
        externalRequests.add(request)
        return externalResult ?: super.launchExternalApp(request)
    }
    override fun createVoiceInput(): VoiceInput = AndroidVoiceInput(applicationContext)
    override fun createKnowledgeLibrary(): LocalKnowledgeLibrary = LocalKnowledgeLibrary(applicationContext,
        "nexus-default", LocalKnowledgeLibraryTest.JvmCipher("nexus-default"))
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@LooperMode(LooperMode.Mode.PAUSED)
class MainActivityTest {
    @Test fun aFailedReadingCannotApproveAnEarlierMemoryProposal() {
        onlineAccess().isChecked = false
        activity.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("vide", ""))
        for ((command, failure) in listOf(
            "lis la réponse IA de Chrome" to R.string.external_online_disabled,
            "lis le texte copié" to R.string.chrome_clipboard_empty
        )) {
            send("retiens que je préfère le thé vert")
            await { hasText("Tu confirmes son enregistrement local") }
            send(command)
            await { hasText(activity.getString(failure)) }
            assertEquals(View.GONE, activity.findViewById<Button>(R.id.home_retain).visibility)
            send("oui")
            await { hasText("aucune recherche") || hasText("enregistrée") }
            assertTrue("The earlier proposal survived $command", localLibrary().list().isEmpty())
        }
    }
    @Test fun chromeReadingStartsDisabledAndItsSetupCanBeCancelledWithoutGrantingAnything() {
        val toggle = activity.findViewById<Switch>(R.id.home_chrome_auto_read)
        assertFalse(toggle.isChecked)
        toggle.isChecked = true
        shadowOf(Looper.getMainLooper()).idle()
        val dialog = ShadowAlertDialog.getLatestAlertDialog()
        assertTrue(dialog.findViewById<TextView>(android.R.id.message).text.contains("réglages Android"))
        dialog.cancel()
        shadowOf(Looper.getMainLooper()).idle()
        assertFalse(toggle.isChecked)
        assertFalse(activity.getSharedPreferences("nexus_online_preferences", Context.MODE_PRIVATE).getBoolean("chrome_ai_reading_enabled", false))
        assertNull(ChromeAiReaderService.session.active())
        assertEquals(0, lifecycle.get().chromeLaunches)
    }

    @Test fun aSpokenCompatibleReadCommandOpensSetupUntilTheAccessIsEnabled() {
        send("Nexus, lis la réponse IA de Chrome")
        shadowOf(Looper.getMainLooper()).idle()
        val dialog = ShadowAlertDialog.getLatestAlertDialog()
        assertTrue(dialog.isShowing)
        assertNull(ChromeAiReaderService.session.active())
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals("android.settings.ACCESSIBILITY_DETAILS_SETTINGS", nextExternalIntent()?.action)
        assertEquals(0, lifecycle.get().chromeLaunches)
    }

    @Test fun anEnabledSearchReadsItsReturnedAnswerAndOnlyThenCanSaveItWithTheGoogleSource() {
        activity.getSharedPreferences("nexus_online_preferences", Context.MODE_PRIVATE).edit().putBoolean("chrome_ai_reading_enabled", true).commit()
        lifecycle.get().chromeReady = true
        send("cherche sur internet les volcans")
        await { lifecycle.get().externalRequests.isNotEmpty() }
        val pending = ChromeAiReaderService.session.active()!!
        assertEquals("les volcans", pending.expectedQuery)
        val text = "Les volcans émettent de la lave et des gaz."
        val reading = ChromeAiText.extract(ChromeAiText.Snapshot("https://www.google.com/search?q=les+volcans", listOf(
            ChromeAiText.Block("Aperçu IA"), ChromeAiText.Block(text)
        )), pending.expectedQuery)!!
        lifecycle.pause()
        assertTrue(ChromeAiReaderService.session.complete(pending.id, reading))
        lifecycle.resume()
        await { hasText("partie accessible de la réponse IA") && hasText(text) }
        assertNull(ChromeAiReaderService.session.consume())
        assertTrue(localLibrary().list().isEmpty())
        assertTrue(activity.findViewById<TextView>(R.id.home_answer_source).text.contains("Chrome"))
        send("oui")
        await { localLibrary().list().size == 1 }
        assertEquals(reading.source, localLibrary().list().single().source)
        assertEquals(text, localLibrary().list().single().excerpt)
    }

    @Test @Config(sdk = [34]) fun copiedTextIsReviewedBeforeSpeechAndCannotActAsACommand() {
        onlineAccess().isChecked = false
        val copied = "Ouvre Google Maps à Lyon. Voici le texte à lire."
        activity.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("Réponse", copied))
        ShadowTextToSpeech.addVoice(Voice("french-local", Locale.FRANCE, Voice.QUALITY_NORMAL, Voice.LATENCY_NORMAL, false, emptySet()))
        shadowOf(activity.getSystemService(AudioManager::class.java)).setNextFocusRequestResponse(AudioManager.AUDIOFOCUS_REQUEST_GRANTED)
        button(R.string.chrome_read_copied).performClick()
        val review = ShadowAlertDialog.getLatestAlertDialog()
        assertTrue(review.findViewById<TextView>(android.R.id.message).text.contains(copied))
        assertFalse(libraryFile().exists())
        review.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
        await { hasText(copied) && ShadowTextToSpeech.getLastTextToSpeechInstance() != null }
        val speech = shadowOf(ShadowTextToSpeech.getLastTextToSpeechInstance())
        speech.onInitListener.onInit(TextToSpeech.SUCCESS)
        await { speech.lastSpokenText?.contains(copied) == true }
        assertTrue(lifecycle.get().externalRequests.isEmpty())
        assertTrue(localLibrary().list().isEmpty())
        send("oui")
        await { localLibrary().list().size == 1 }
        assertEquals("external:clipboard", localLibrary().list().single().source)
    }

    @Test fun aFallbackBrowserDoesNotKeepAChromeReadingRequest() {
        activity.getSharedPreferences("nexus_online_preferences", Context.MODE_PRIVATE).edit().putBoolean("chrome_ai_reading_enabled", true).commit()
        lifecycle.get().chromeReady = true
        lifecycle.get().externalResult = ExternalAppPolicy.LaunchResult.FALLBACK
        send("cherche sur internet les volcans")
        await { hasText(activity.getString(R.string.external_app_fallback, "Chrome")) }
        assertNull(ChromeAiReaderService.session.active())
        assertNull(ChromeAiReaderService.session.consume())
    }

    @Test fun returningEarlyCancelsAReaderAndCannotDeliverItsLateResult() {
        activity.getSharedPreferences("nexus_online_preferences", Context.MODE_PRIVATE).edit().putBoolean("chrome_ai_reading_enabled", true).commit()
        lifecycle.get().chromeReady = true
        button(R.string.chrome_read).performClick()
        val pending = ChromeAiReaderService.session.active()!!
        lifecycle.pause().resume()
        assertTrue(hasText(activity.getString(R.string.chrome_read_cancelled)))
        assertFalse(ChromeAiReaderService.session.complete(pending.id, BrowserReading.copied("Une ancienne réponse que Nexus ne doit pas lire.")))
        assertEquals(1, lifecycle.get().chromeLaunches)
    }
    @Test fun anInternetRequestOpensTheWebInsteadOfClaimingToHaveReadWikipedia() {
        send("cherche sur internet les réponses de salutations")
        await { hasText("recherche Web dans le navigateur") }
        val intent=nextExternalIntent()!!
        assertEquals("com.android.chrome",intent.`package`)
        assertEquals("les réponses de salutations",intent.data?.getQueryParameter("q"))
        assertEquals(View.GONE,activity.findViewById<TextView>(R.id.home_answer_source).visibility)
    }
    @Test fun aMisheardPrefixDoesNotOpenAnythingBeforeAVoiceCompatibleConfirmation() {
        send("cher sur interdit que répondre bonjour")
        await { hasText("mal compris le début") }
        assertNull(nextExternalIntent())
        send("oui")
        await { hasText("recherche Web dans le navigateur") }
        assertEquals("que répondre bonjour",nextExternalIntent()!!.data?.getQueryParameter("q"))
    }
    @Test fun fallbackIsReportedWithoutClaimingThatTheNamedAppOpenedAndRepeatDoesNotLaunchAgain() {
        val fixture=lifecycle.get();fixture.externalResult=ExternalAppPolicy.LaunchResult.FALLBACK
        send("ouvre Chrome")
        await { hasText(activity.getString(R.string.external_app_fallback,"Chrome")) }
        assertEquals(1,fixture.externalRequests.size)
        assertFalse(hasText("J’ouvre Chrome."))
        send("répète")
        await { hasText("Je n’ai plus de réponse récente") }
        assertEquals(1,fixture.externalRequests.size)
    }
    @Test fun anUnavailableExternalAppReportsFailureAndTheConversationRemainsUsable() {
        val fixture=lifecycle.get();fixture.externalResult=ExternalAppPolicy.LaunchResult.UNAVAILABLE
        send("ouvre Google Maps")
        await { hasText(activity.getString(R.string.external_app_unavailable)) }
        assertEquals(1,fixture.externalRequests.size)
        send("bonjour")
        await { hasText("Bonjour") }
        assertEquals(1,fixture.externalRequests.size)
    }
    @Test fun explicitGoogleSearchOpensChromeWithOnlyTheRequestedTextAndStopsTheBody() {
        send("avance de vingt centimètres")
        await { hasText("roule en avant") }
        send("cherche sur Google musée Lyon")
        await { hasText("J’ouvre Chrome") }
        val intent=nextExternalIntent()!!
        assertEquals("com.android.chrome",intent.`package`)
        assertEquals("musée Lyon",intent.data?.getQueryParameter("q"))
        assertNull(intent.extras)
        assertFalse(activity.findViewById<NexusAvatarView>(R.id.face_mode_avatar).bodyFrame.moving)
    }
    @Test fun aRememberedPlaceIsOnlySentToMapsAfterConfirmationInTheUi() {
        localLibrary().addUserNote("Adresse domicile","12 rue Exemple, Lyon")
        send("itinéraire vers chez moi")
        await { hasText("Tu confirmes l’envoi") }
        assertNull(nextExternalIntent())
        send("oui")
        await { hasText("J’ouvre Google Maps") }
        val intent=nextExternalIntent()!!
        assertEquals("com.google.android.apps.maps",intent.`package`)
        assertEquals("12 rue Exemple, Lyon",intent.data?.getQueryParameter("destination"))
        assertEquals(setOf("api","destination"),intent.data?.queryParameterNames)
    }
    @Test fun backgroundingBeforeTheWorkerReplyPreventsAnOldAppOpeningAfterResume() {
        send("ouvre Chrome")
        lifecycle.pause()
        lifecycle.resume()
        await { hasText(activity.getString(R.string.external_cancelled)) }
        assertNull(nextExternalIntent())
    }
    @Test fun disablingOnlineAccessPreventsBrowserAndMapsHandoffs() {
        onlineAccess().performClick()
        send("ouvre Chrome")
        await { hasText(activity.getString(R.string.external_online_disabled)) }
        assertNull(nextExternalIntent())
    }
    @Test fun voiceMovementOpensTheRollingBodyAndStopAndSteeringRemainAvailable() {
        send("avance de vingt centimètres")
        await { hasText("roule en avant") }
        val avatar = activity.findViewById<NexusAvatarView>(R.id.face_mode_avatar)
        assertTrue(avatar.showBody)
        assertTrue(activity.findViewById<View>(R.id.body_controls).isShown)
        assertTrue(avatar.bodyFrame.moving)
        activity.findViewById<Button>(R.id.body_stop).performClick()
        assertFalse(avatar.bodyFrame.moving)
        activity.findViewById<Button>(R.id.body_steering).performClick()
        assertFalse(avatar.bodyFrame.steeringAtFront)
        lifecycle.recreate(); activity = lifecycle.get()
        assertFalse(activity.findViewById<NexusAvatarView>(R.id.face_mode_avatar).bodyFrame.moving)
        assertFalse(activity.findViewById<NexusAvatarView>(R.id.face_mode_avatar).bodyFrame.steeringAtFront)
    }

    @Test fun leavingTheRobotStopsItsVirtualMotionAndKeepsFullFaceAccessible() {
        send("avance de vingt centimètres")
        await { hasText("roule en avant") }
        button(R.string.leave_face).performClick()
        assertFalse(activity.findViewById<NexusAvatarView>(R.id.face_mode_avatar).bodyFrame.moving)
        button(R.string.full_face_mode).performClick()
        assertFalse(activity.findViewById<View>(R.id.body_controls).isShown)
        assertTrue(activity.findViewById<View>(R.id.face_mode_screen).isShown)
    }
    @Test fun robotModeRestoresItsBodyAndFullFaceRemainsAvailable() {
        button(R.string.full_robot_mode).performClick()
        assertTrue(activity.findViewById<NexusAvatarView>(R.id.face_mode_avatar).showBody)
        lifecycle.recreate(); activity = lifecycle.get()
        assertTrue(activity.findViewById<NexusAvatarView>(R.id.face_mode_avatar).showBody)
        button(R.string.leave_face).performClick()
        button(R.string.full_face_mode).performClick()
        assertFalse(activity.findViewById<NexusAvatarView>(R.id.face_mode_avatar).showBody)
        assertTrue(activity.findViewById<View>(R.id.face_mode_screen).isShown)
    }

    @Test fun displayCommandsWorkFromTheConversationWithoutOpeningADialog() {
        send("Nexus, affiche ton corps")
        assertTrue(activity.findViewById<NexusAvatarView>(R.id.face_mode_avatar).showBody)
        assertTrue(activity.findViewById<View>(R.id.face_mode_screen).isShown)
        send("affiche ton visage")
        assertFalse(activity.findViewById<NexusAvatarView>(R.id.face_mode_avatar).showBody)
        send("retour à l’accueil")
        assertTrue(activity.findViewById<View>(R.id.home_scroll).isShown)
        assertNull(ShadowAlertDialog.getLatestAlertDialog())
    }

    private lateinit var lifecycle: ActivityController<MainActivityWithAndroidVoice>
    private lateinit var activity: MainActivity
    @Before fun createActivity() {
        lifecycle = Robolectric.buildActivity(MainActivityWithAndroidVoice::class.java).setup()
        activity = lifecycle.get()
    }
    @After fun destroyActivity() { ChromeAiReaderService.cancelReading(); lifecycle.pause().stop().destroy() }
    private fun views(view: View = activity.findViewById(android.R.id.content)): List<View> =
        listOf(view) + if (view is ViewGroup) (0 until view.childCount).flatMap { views(view.getChildAt(it)) } else emptyList()
    private fun input() = views().filterIsInstance<EditText>().single()
    private fun spokenReplies() = activity.findViewById<Switch>(R.id.home_spoken_replies)
    private fun onlineAccess() = activity.findViewById<Switch>(R.id.home_online_access)
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
    private fun nextExternalIntent(): Intent? {
        var intent=shadowOf(activity).nextStartedActivity
        // The initial microphone permission prompt is an independent Android activity.
        while (intent?.action=="android.content.pm.action.REQUEST_PERMISSIONS") intent=shadowOf(activity).nextStartedActivity
        return intent
    }
    private fun libraryFile() = File(activity.filesDir, "knowledge-nexus-default.json")
    private fun localLibrary()=LocalKnowledgeLibrary(activity.applicationContext,"nexus-default",LocalKnowledgeLibraryTest.JvmCipher("nexus-default"))
    private fun openNoteDetails() : AlertDialog {
        input().setText("")
        val previous=ShadowAlertDialog.getLatestAlertDialog()
        button(R.string.my_notes).performClick()
        await { ShadowAlertDialog.getLatestAlertDialog() !== previous && ShadowAlertDialog.getLatestAlertDialog()?.listView != null }
        val list=ShadowAlertDialog.getLatestAlertDialog()
        list.listView.performItemClick(null,0,0)
        shadowOf(Looper.getMainLooper()).idle()
        return ShadowAlertDialog.getLatestAlertDialog()
    }

    @Test fun editingRequiresAnExplicitSaveAndRetainsTheFullOldVersion() {
        val library=localLibrary();val original=library.addUserNote("Métier","Je travaille comme technicien.").entry
        val details=openNoteDetails()
        details.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
        shadowOf(Looper.getMainLooper()).idle()
        val editor=ShadowAlertDialog.getLatestAlertDialog()
        assertEquals(original.excerpt,editor.findViewById<EditText>(R.id.note_edit_body).text.toString())
        editor.findViewById<EditText>(R.id.note_edit_body).setText("Je travaille comme ingénieur.")
        editor.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
        shadowOf(Looper.getMainLooper()).idle()
        await { hasText(activity.getString(R.string.note_updated)) }
        val current=library.list().single()
        assertEquals("Je travaille comme ingénieur.",current.excerpt)
        assertEquals(original.excerpt,library.history(current.id).single().excerpt)
        send("Quel est mon métier ?")
        await { hasText("ingénieur") }
        assertFalse(hasText("technicien"))
    }
    @Test fun cancelingANoteEditPreservesItsCiphertextAndDoesNotCreateHistory() {
        val library=localLibrary();val original=library.addUserNote("Note","Texte à conserver.").entry
        val before=libraryFile().readBytes()
        openNoteDetails().getButton(AlertDialog.BUTTON_POSITIVE).performClick()
        shadowOf(Looper.getMainLooper()).idle()
        val editor=ShadowAlertDialog.getLatestAlertDialog()
        editor.findViewById<EditText>(R.id.note_edit_body).setText("Brouillon non enregistré.")
        editor.getButton(AlertDialog.BUTTON_NEGATIVE).performClick()
        shadowOf(Looper.getMainLooper()).idle()
        assertArrayEquals(before,libraryFile().readBytes())
        assertTrue(library.history(original.id).isEmpty())
    }
    @Test fun aStaleEditorCannotOverwriteAnotherLibraryInstancesCorrection() {
        val library=localLibrary();val original=library.addUserNote("Note","Texte original.").entry
        openNoteDetails().getButton(AlertDialog.BUTTON_POSITIVE).performClick()
        shadowOf(Looper.getMainLooper()).idle()
        val editor=ShadowAlertDialog.getLatestAlertDialog()
        library.replace(original,"Note","Modification extérieure.")
        val before=libraryFile().readBytes()
        editor.findViewById<EditText>(R.id.note_edit_body).setText("Ancien formulaire.")
        editor.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
        shadowOf(Looper.getMainLooper()).idle()
        await { hasText(activity.getString(R.string.note_changed)) }
        assertArrayEquals(before,libraryFile().readBytes())
        assertEquals("Modification extérieure.",library.list().single().excerpt)
    }
    @Test fun previousVersionsCanBeRestoredThroughAnExplicitReview() {
        val library=localLibrary();val original=library.addUserNote("Note","Ancien contenu.").entry
        val revised=library.replace(original,"Note","Contenu actuel.").entry
        val details=openNoteDetails()
        details.findViewById<Button>(R.id.note_history_button).performClick()
        shadowOf(Looper.getMainLooper()).idle()
        await { ShadowAlertDialog.getLatestAlertDialog() !== details && ShadowAlertDialog.getLatestAlertDialog()?.listView != null }
        val history=ShadowAlertDialog.getLatestAlertDialog()
        assertEquals(1,history.listView.adapter.count)
        history.listView.performItemClick(null,0,0)
        shadowOf(Looper.getMainLooper()).idle()
        val old=ShadowAlertDialog.getLatestAlertDialog()
        assertTrue(old.findViewById<TextView>(android.R.id.message).text.contains(original.excerpt))
        old.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
        shadowOf(Looper.getMainLooper()).idle()
        val confirmation=ShadowAlertDialog.getLatestAlertDialog()
        assertEquals(revised.id,library.list().single().id)
        confirmation.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
        shadowOf(Looper.getMainLooper()).idle()
        await { hasText(activity.getString(R.string.note_restored)) }
        val restored=library.list().single()
        assertEquals(original.excerpt,restored.excerpt)
        assertEquals(2,library.history(restored.id).size)
    }
    @Test fun theFocusedVoiceCommandOpensTheCorrectEditorWithoutSavingAnything() {
        val library=localLibrary();val original=library.addUserNote("Métier","Je travaille comme technicien.").entry
        val before=libraryFile().readBytes()
        send("Quel est mon métier ?")
        await { hasText("technicien") }
        send("Nexus, modifie cette note")
        await { ShadowAlertDialog.getLatestAlertDialog()?.findViewById<EditText>(R.id.note_edit_body) != null }
        val editor=ShadowAlertDialog.getLatestAlertDialog()
        assertEquals(original.excerpt,editor.findViewById<EditText>(R.id.note_edit_body).text.toString())
        assertArrayEquals(before,libraryFile().readBytes())
        editor.getButton(AlertDialog.BUTTON_NEGATIVE).performClick()
        shadowOf(Looper.getMainLooper()).idle()
    }
    @Test fun permanentDeletionExplicitlyIncludesTheVersionsAndKeepsOtherNotes() {
        val library=localLibrary();val original=library.addUserNote("Note","Ancien contenu.").entry
        library.replace(original,"Note","Contenu actuel.")
        openNoteDetails().getButton(AlertDialog.BUTTON_NEUTRAL).performClick()
        shadowOf(Looper.getMainLooper()).idle()
        val confirmation=ShadowAlertDialog.getLatestAlertDialog()
        assertTrue(confirmation.findViewById<TextView>(android.R.id.message).text.contains("anciennes versions"))
        confirmation.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
        shadowOf(Looper.getMainLooper()).idle()
        await { hasText(activity.getString(R.string.note_deleted)) }
        assertTrue(library.list().isEmpty())
        assertEquals(0,library.statistics().archivedNotes)
    }


    @Test @Config(sdk = [34]) fun typedConversationIsSpokenWithMicrophoneAndOnlineAccessDisabled() {
        assertEquals(PackageManager.PERMISSION_DENIED, activity.checkSelfPermission(android.Manifest.permission.RECORD_AUDIO))
        onlineAccess().isChecked = false
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

    @Test fun onlineAccessStartsEnabledAndKeepsTheChoiceAfterRecreation() {
        assertTrue(onlineAccess().isChecked)
        assertTrue(hasText(activity.getString(R.string.online_allowed)))
        onlineAccess().performClick()
        lifecycle.recreate()
        activity = lifecycle.get()
        assertFalse(onlineAccess().isChecked)
        assertTrue(hasText(activity.getString(R.string.online_disabled_badge)))

        send("Nexus, cherche sur Internet la tour Eiffel")
        await { hasText(activity.getString(R.string.online_disabled_reply)) }
        assertEquals(View.GONE, activity.findViewById<TextView>(R.id.home_answer_source).visibility)
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
        val activeRecognizer = ShadowSpeechRecognizer.getLatestSpeechRecognizer()
        val recognizer = shadowOf(activeRecognizer)
        val avatar = activity.findViewById<NexusAvatarView>(R.id.nexus_avatar)
        assertEquals(CompanionPresence.READY, avatar.presence)
        recognizer.triggerOnReadyForSpeech(android.os.Bundle())
        assertEquals(CompanionPresence.LISTENING, avatar.presence)
        assertTrue(hasText(activity.getString(R.string.home_presence_listening)))
        assertTrue(hasText("Micro actif"))
        assertTrue(button(R.string.speak_now).isEnabled)

        button(R.string.full_face_mode).performClick()
        val fullFace = activity.findViewById<NexusAvatarView>(R.id.face_mode_avatar)
        assertTrue(fullFace.isShown)
        assertEquals(CompanionPresence.LISTENING, fullFace.presence)
        assertSame(activeRecognizer, ShadowSpeechRecognizer.getLatestSpeechRecognizer())
        button(R.string.leave_face).performClick()
        assertEquals(CompanionPresence.LISTENING, avatar.presence)
        assertSame(activeRecognizer, ShadowSpeechRecognizer.getLatestSpeechRecognizer())

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

        val controlIds = listOf(R.id.home_face_mode, R.id.home_robot_mode, R.id.home_microphone, R.id.home_spoken_replies, R.id.home_speak_now,
            R.id.home_input, R.id.home_send, R.id.home_notes, R.id.home_remember, R.id.home_updates, R.id.home_online_access)
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

    @Test fun fullFaceExitRestoresTheSameHomeAndKeepsTheDraft() {
        val home = activity.findViewById<ScrollView>(R.id.home_scroll)
        val face = activity.findViewById<View>(R.id.face_mode_screen)
        input().setText("Note en cours de rédaction")
        assertTrue(home.isShown)
        assertFalse(face.isShown)

        button(R.string.full_face_mode).performClick()
        assertTrue(face.isShown)
        assertFalse(home.isShown)
        assertEquals(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE,
            activity.window.insetsController?.systemBarsBehavior)
        button(R.string.leave_face).performClick()
        assertFalse(face.isShown)
        assertTrue(home.isShown)
        assertEquals(WindowInsetsController.BEHAVIOR_DEFAULT, activity.window.insetsController?.systemBarsBehavior)
        assertEquals("Note en cours de rédaction", input().text.toString())
        assertFalse(libraryFile().exists())
    }

    @Test @Config(qualifiers = "w640dp-h320dp-land-mdpi") fun fullFaceRestoresAfterRecreationAndFitsInLandscape() {
        button(R.string.full_face_mode).performClick()
        RuntimeEnvironment.setFontScale(1.5f)
        lifecycle.recreate()
        activity = lifecycle.get()
        val face = activity.findViewById<View>(R.id.face_mode_screen)
        assertTrue(face.isShown)
        assertFalse(activity.findViewById<ScrollView>(R.id.home_scroll).isShown)

        face.measure(View.MeasureSpec.makeMeasureSpec(640, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(320, View.MeasureSpec.EXACTLY))
        face.layout(0, 0, 640, 320)
        val portrait = activity.findViewById<NexusAvatarView>(R.id.face_mode_avatar)
        val status = activity.findViewById<TextView>(R.id.face_mode_presence)
        val exit = button(R.string.leave_face)
        assertTrue(exit.height >= 48)
        assertTrue(exit.right <= face.width)
        assertEquals(face.width, portrait.width)
        assertEquals(face.height, portrait.height)
        assertTrue(exit.bottom < status.top)
        assertTrue(status.bottom <= face.height)

        exit.performClick()
        assertTrue(activity.findViewById<ScrollView>(R.id.home_scroll).isShown)
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
    @Test fun everyNoteRemainsReachableBeyondTheFirstFifty() {
        val library = LocalKnowledgeLibrary(activity.applicationContext, "nexus-default", LocalKnowledgeLibraryTest.JvmCipher("nexus-default"))
        repeat(61) { library.addUserNote("Note $it", "Information locale $it.") }
        button(R.string.my_notes).performClick()
        await { ShadowAlertDialog.getLatestAlertDialog()?.isShowing == true }
        val first = ShadowAlertDialog.getLatestAlertDialog()
        assertEquals(50, first.listView.adapter.count)
        val reachable = (0 until 50).map { first.listView.adapter.getItem(it).toString() }.toMutableSet()
        first.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
        await { ShadowAlertDialog.getLatestAlertDialog() !== first }
        val second = ShadowAlertDialog.getLatestAlertDialog()
        assertEquals(11, second.listView.adapter.count)
        reachable.addAll((0 until 11).map { second.listView.adapter.getItem(it).toString() })
        assertEquals((0 until 61).map { "Note $it" }.toSet(), reachable)
        second.getButton(AlertDialog.BUTTON_NEUTRAL).performClick()
        await { ShadowAlertDialog.getLatestAlertDialog() !== second }
        assertEquals(50, ShadowAlertDialog.getLatestAlertDialog().listView.adapter.count)
        assertEquals(61, library.list().size)
    }

    @Test fun damagedLibraryDoesNotBlockGreetingOrEraseTheOriginalFile() {
        val original = "unreadable library bytes".toByteArray()
        libraryFile().writeBytes(original)
        send("bonjour")
        await { hasText("Bonjour ! Je t’écoute") && hasText(activity.getString(R.string.library_read_failed)) }
        assertArrayEquals(original, libraryFile().readBytes())
    }

    @Test fun longInputIsBoundedBeforeItCanReachTheWorker() {
        input().setText("x".repeat(16500))
        assertEquals(16000, input().length())
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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31])
class LegacyFullFaceModeTest {
    @Test fun android12BackExitsTheFaceBeforeLeavingTheActivity() {
        // An attached Android 12 test window initializes Robolectric's incompatible Bluetooth shadow.
        val lifecycle = Robolectric.buildActivity(MainActivity::class.java).create()
        try {
            val activity = lifecycle.get()
            activity.findViewById<Button>(R.id.home_face_mode).performClick()
            val face = activity.findViewById<View>(R.id.face_mode_screen)
            assertEquals(View.VISIBLE, face.visibility)

            activity.onBackPressed()
            assertEquals(View.GONE, face.visibility)
            assertEquals(View.VISIBLE, activity.findViewById<View>(R.id.home_scroll).visibility)
            assertFalse(activity.isFinishing)
        } finally {
            lifecycle.destroy()
        }
    }
}
