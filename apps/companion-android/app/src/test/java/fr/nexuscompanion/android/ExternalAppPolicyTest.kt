package fr.nexuscompanion.android

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

class ExternalRecordingActivity : Activity() {
    val attempts = mutableListOf<Intent>()
    var missingPreferred = false
    var unavailable = false
    var denied = false
    override fun startActivity(intent: Intent) {
        attempts.add(intent)
        if (denied) throw SecurityException("Unavailable")
        if (unavailable || missingPreferred && intent.`package` != null) throw ActivityNotFoundException()
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ExternalAppPolicyTest {
    private fun open(text: String) = (ExternalAppPolicy.parse(text) as ExternalAppPolicy.Command.Open).request

    @Test fun explicitCommandsSelectTheNamedAppAndEncodeOnlyTheRequestedQuery() {
        val maps = open("Nexus, peux-tu ouvrir Google Maps à Lyon ?")
        assertEquals(ExternalAppPolicy.App.MAPS, maps.app)
        assertEquals("Lyon", maps.uri().getQueryParameter("query"))
        assertEquals("1", maps.uri().getQueryParameter("api"))
        val search = open("cherche sur Google café & théâtre à Lyon")
        assertEquals(ExternalAppPolicy.App.CHROME, search.app)
        assertEquals("café & théâtre à Lyon", search.uri().getQueryParameter("q"))
        assertEquals(setOf("q"), search.uri().queryParameterNames)
        assertEquals("https", search.uri().scheme)
        assertEquals("www.google.com", search.uri().host)
    }
    @Test fun directionsOpenAPreviewWithoutAnOriginOrAutomaticNavigation() {
        val route = open("prépare un itinéraire vers 12 rue Exemple, Lyon")
        assertEquals(ExternalAppPolicy.Operation.DIRECTIONS, route.operation)
        assertEquals(setOf("api", "destination"), route.uri().queryParameterNames)
        assertEquals("12 rue Exemple, Lyon", route.uri().getQueryParameter("destination"))
        assertTrue(ExternalAppPolicy.parse("itinéraire vers chez moi") is ExternalAppPolicy.Command.SavedPlace)
        assertTrue(ExternalAppPolicy.parse("ouvre Google Maps pour mon travail") is ExternalAppPolicy.Command.SavedPlace)
    }
    @Test fun bareCommandsOpenHomeAndDirectHttpsPagesPreserveCaseAndParameters() {
        assertEquals(ExternalAppPolicy.Operation.HOME, open("ouvre Google Chrome").operation)
        assertEquals(ExternalAppPolicy.Operation.HOME, open("ouvre Google Maps").operation)
        val url="https://example.com/Path?x=A%26B"
        assertEquals(url, open("ouvre dans Chrome $url").uri().toString())
        assertEquals(url, open("ouvre Chrome $url").uri().toString())
        assertEquals(url, open("ouvre $url dans Chrome").uri().toString())
    }
    @Test fun untrustedSchemesCredentialsBlankTargetsAndOversizedOrSecretQueriesNeverProduceActions() {
        for (url in listOf("javascript:alert(1)", "intent://example", "file:///data/a", "http://example.com", "https://u:p@example.com", "https://example.com/?access_token=secret"))
            assertTrue(url,ExternalAppPolicy.parse("ouvre dans Chrome $url") is ExternalAppPolicy.Command.Clarify)
        for (command in listOf("cherche sur Google", "cherche sur Google mon mot de passe est secret", "cherche sur Google "+"a".repeat(301), "ouvre Maps à Lyon\nsecret"))
            assertTrue(command,ExternalAppPolicy.parse(command) == null || ExternalAppPolicy.parse(command) is ExternalAppPolicy.Command.Clarify)
        assertThrows(IllegalArgumentException::class.java) { ExternalAppPolicy.Request(ExternalAppPolicy.App.CHROME,ExternalAppPolicy.Operation.URL,"https://example.com\\@evil.com") }
        assertNull(ExternalAppPolicy.parse("Ma note dit ouvre Chrome"))
        assertNull(ExternalAppPolicy.parse("retiens que je dois ouvrir Google Maps"))
    }
    @Test fun launcherPrefersTheNamedAppAndFallsBackWithoutCrashOrExtraData() {
        val controller=Robolectric.buildActivity(ExternalRecordingActivity::class.java).setup()
        try {
            val activity=controller.get();activity.missingPreferred=true
            val request=open("itinéraire vers Lyon")
            assertTrue(ExternalAppPolicy.launch(activity,request))
            assertEquals(listOf("com.google.android.apps.maps",null),activity.attempts.map { it.`package` })
            assertTrue(activity.attempts.all { it.action==Intent.ACTION_VIEW && it.extras==null && it.hasCategory(Intent.CATEGORY_BROWSABLE) })
            activity.unavailable=true;assertFalse(ExternalAppPolicy.launch(activity,request))
            activity.denied=true;assertFalse(ExternalAppPolicy.launch(activity,request))
        } finally { controller.pause().stop().destroy() }
    }
}
