package fr.nexuscompanion.android

import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@Suppress("DEPRECATION")
class ChromeAccessibilityTest {
    private fun node(text: String = "", type: String = "android.view.View", pkg: String = ChromeAiReaderService.CHROME) =
        AccessibilityNodeInfo.obtain().apply {
            packageName = pkg
            className = type
            this.text = text
            isVisibleToUser = true
        }

    private fun AccessibilityNodeInfo.child(child: AccessibilityNodeInfo): AccessibilityNodeInfo {
        shadowOf(this).addChild(child)
        return child
    }

    @Test fun capturesOnlyVisibleDocumentTextAndDoesNotReadEditableOrPasswordSubtrees() {
        val lifecycle = Robolectric.buildService(ChromeAiReaderService::class.java).create()
        try {
            val root = node(type = "android.widget.FrameLayout")
            root.child(node("www.google.com/search?q=les+volcans", "android.widget.EditText").apply {
                viewIdResourceName = "com.android.chrome:id/url_bar"
            })
            val web = root.child(node(type = "android.webkit.WebView"))
            web.child(node("Aperçu IA").apply { isHeading = true })
            web.child(node("Les volcans émettent de la lave et des gaz."))
            web.child(node("Invisible").apply { isVisibleToUser = false })
            web.child(node(type = "android.widget.EditText").apply { isEditable = true }).child(node("Texte saisi privé"))
            web.child(node(type = "android.widget.EditText").apply { isPassword = true }).child(node("Secret privé"))
            val captured = lifecycle.get().snapshot(root, SystemClock.elapsedRealtime() + 5000)!!
            val result = ChromeAiText.extract(captured, "les volcans")!!
            assertEquals("Les volcans émettent de la lave et des gaz.", result.text)
            assertFalse(captured.blocks.any { it.text.contains("privé") })
        } finally { lifecycle.destroy() }
    }

    @Test fun aFakeAddressInsideThePageAndAnotherAppCannotEstablishTheOrigin() {
        val lifecycle = Robolectric.buildService(ChromeAiReaderService::class.java).create()
        try {
            val root = node()
            val web = root.child(node(type = "android.webkit.WebView"))
            web.child(node("www.google.com/search?q=volcan").apply { viewIdResourceName = "com.android.chrome:id/url_bar" })
            web.child(node("Aperçu IA"))
            web.child(node("Un faux texte qui ne prouve pas son origine."))
            val snapshot = lifecycle.get().snapshot(root, SystemClock.elapsedRealtime() + 5000)!!
            assertNull(snapshot.address)
            assertNull(ChromeAiText.extract(snapshot, null))
            assertNull(lifecycle.get().snapshot(node("Texte d’une autre application", pkg = "other.app"), SystemClock.elapsedRealtime() + 5000))
        } finally { lifecycle.destroy() }
    }

    @Test fun anExpiredCaptureDoesNotTraverseTheDocument() {
        val lifecycle = Robolectric.buildService(ChromeAiReaderService::class.java).create()
        try {
            assertNull(lifecycle.get().snapshot(node(), SystemClock.elapsedRealtime()))
            assertNull(lifecycle.get().snapshot(node(), SystemClock.elapsedRealtime() + 5000) { false })
        } finally { lifecycle.destroy() }
    }

    @Test fun theSystemBindingAndConfigurationRestrictTheServiceWithoutGestureCapabilities() {
        val context = RuntimeEnvironment.getApplication()
        val component = ComponentName(context, ChromeAiReaderService::class.java)
        val service = context.packageManager.getServiceInfo(component, PackageManager.GET_META_DATA)
        assertEquals("android.permission.BIND_ACCESSIBILITY_SERVICE", service.permission)
        assertTrue(service.exported)
        context.resources.getXml(service.metaData.getInt("android.accessibilityservice")).use { xml ->
            while (xml.eventType != org.xmlpull.v1.XmlPullParser.START_TAG) xml.next()
            val ns = "http://schemas.android.com/apk/res/android"
            assertEquals("com.android.chrome", xml.getAttributeValue(ns, "packageNames"))
            assertEquals("false", xml.getAttributeValue(ns, "canPerformGestures"))
            assertEquals("true", xml.getAttributeValue(ns, "canRetrieveWindowContent"))
        }
        val intent = ChromeAiReaderService.settingsIntent(context)
        assertEquals(component.flattenToString(), intent.getStringExtra(Intent.EXTRA_COMPONENT_NAME))
    }
}
