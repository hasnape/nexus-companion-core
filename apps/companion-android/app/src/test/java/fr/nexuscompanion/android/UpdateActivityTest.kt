package fr.nexuscompanion.android

import android.app.Activity
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.Signature
import android.content.pm.SigningInfo
import android.net.Uri
import android.os.Bundle
import android.os.Looper
import android.provider.Settings
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import org.junit.After
import org.junit.Before
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import org.robolectric.shadow.api.Shadow
import java.io.File
import java.security.MessageDigest

/** Android metadata and installer are simulated; the private snapshot and URI bytes are real. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@LooperMode(LooperMode.Mode.PAUSED)
class UpdateActivityTest {
    private lateinit var lifecycle: ActivityController<UpdateActivity>
    private lateinit var activity: UpdateActivity
    @Before fun initializePackageServices() {
        shadowOf(RuntimeEnvironment.getApplication().packageManager).setCanRequestPackageInstalls(false)
        // Real Android attaches providers once at startup; each Robolectric case has a new data directory.
        Robolectric.buildContentProvider(UpdateFileProvider::class.java).create()
    }
    private fun create(state: Bundle? = null) {
        lifecycle = Robolectric.buildActivity(UpdateActivity::class.java).create(state).start().resume().visible()
        activity = lifecycle.get()
    }
    @After fun close() { if (::lifecycle.isInitialized) lifecycle.pause().stop().destroy() }
    private fun views(view: View = activity.findViewById(android.R.id.content)): List<View> =
        listOf(view) + if (view is ViewGroup) (0 until view.childCount).flatMap { views(view.getChildAt(it)) } else emptyList()
    private fun button(id: Int) = views().filterIsInstance<Button>().single { it.text.toString() == activity.getString(id) }
    private fun hasText(id: Int) = views().filterIsInstance<TextView>().any { it.text.toString() == activity.getString(id) }
    private fun await(condition: () -> Boolean) {
        val deadline = System.nanoTime() + 10_000_000_000L
        while (System.nanoTime() < deadline) {
            shadowOf(Looper.getMainLooper()).idle()
            if (condition()) return
            Thread.sleep(10)
        }
        fail("Update UI did not reach expected state")
    }
    private fun signing(value: String) = Shadow.newInstanceOf(SigningInfo::class.java).apply {
        shadowOf(this).setSignatures(arrayOf(Signature(value)))
    }
    private fun state(wrongSigner: Boolean = false): Pair<Bundle, File> {
        val context = RuntimeEnvironment.getApplication()
        val manager = shadowOf(context.packageManager)
        manager.getInternalMutablePackageInfo(context.packageName).apply {
            setLongVersionCode(6); signingInfo = signing("010203")
        }
        val dir = File(context.cacheDir, "updates").apply { mkdirs() }
        val file = File(dir, "update-00000000-0000-0000-0000-000000000001.apk").apply { writeText("simulated signed APK bytes") }
        val archive = PackageInfo().apply {
            packageName = context.packageName; setLongVersionCode(7); versionName = "0.4.1-test"
            applicationInfo = ApplicationInfo().apply { minSdkVersion = 31 }
            signingInfo = signing(if (wrongSigner) "040506" else "010203")
        }
        manager.setPackageArchiveInfo(file.absolutePath, archive)
        val hash = MessageDigest.getInstance("SHA-256").digest(file.readBytes()).joinToString("") { "%02x".format(it.toInt() and 255) }
        return Bundle().apply { putString("update_token", file.name); putString("update_hash", hash) } to file
    }
    private fun drainStartedActivities() { while (shadowOf(activity).nextStartedActivity != null) { /* consume previous user actions */ } }

    @Test fun emptyScreenCannotInstallAndRequestsNoNetworkOrBroadStoragePermission() {
        create()
        assertFalse(button(R.string.install_update).isEnabled)
        val permissions = activity.packageManager.getPackageInfo(activity.packageName, PackageManager.GET_PERMISSIONS).requestedPermissions.orEmpty()
        assertTrue(permissions.contains(android.Manifest.permission.REQUEST_INSTALL_PACKAGES))
        assertFalse(permissions.contains(android.Manifest.permission.INTERNET))
        assertFalse(permissions.contains(android.Manifest.permission.MANAGE_EXTERNAL_STORAGE))
    }
    @Test fun pickerUsesTheDocumentGrantAndCancelDoesNotEnableInstallation() {
        create(); button(R.string.choose_apk).performClick()
        val started = shadowOf(activity).nextStartedActivityForResult
        assertEquals(UpdateActivity.PICK_APK, started.requestCode)
        assertEquals(Intent.ACTION_OPEN_DOCUMENT, started.intent.action)
        assertEquals(UpdateActivity.APK_MIME, started.intent.type)
        assertTrue(started.intent.categories.contains(Intent.CATEGORY_OPENABLE))
        shadowOf(activity).receiveResult(started.intent, Activity.RESULT_CANCELED, null)
        assertFalse(button(R.string.install_update).isEnabled)
    }
    @Test fun nonContentUrisAreRejectedWithoutReadingThem() {
        create()
        button(R.string.choose_apk).performClick()
        val request = shadowOf(activity).nextStartedActivityForResult.intent
        shadowOf(activity).receiveResult(request, Activity.RESULT_OK, Intent().setData(Uri.parse("file:///private.apk")))
        assertTrue(hasText(R.string.update_invalid))
        assertFalse(button(R.string.install_update).isEnabled)
    }
    @Test fun grantingInstallPermissionNeverInstallsAutomatically() {
        val (saved, _) = state(); create(saved)
        await { button(R.string.install_update).isEnabled }
        button(R.string.install_update).performClick()
        val started = shadowOf(activity).nextStartedActivityForResult
        assertEquals(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, started.intent.action)
        assertEquals("package:${activity.packageName}", started.intent.data.toString())
        drainStartedActivities()
        shadowOf(activity.packageManager).setCanRequestPackageInstalls(true)
        shadowOf(activity).receiveResult(started.intent, Activity.RESULT_OK, null)
        assertTrue(hasText(R.string.update_permission_ready))
        assertNull(shadowOf(activity).nextStartedActivity)
    }
    @Test fun explicitInstallHandsOnlyTheCheckedSnapshotToAndroidWithReadAccess() {
        val (saved, file) = state(); create(saved)
        await { button(R.string.install_update).isEnabled }
        shadowOf(activity.packageManager).setCanRequestPackageInstalls(true)
        button(R.string.install_update).performClick()
        await { hasText(R.string.update_handed_to_android) }
        val intent = shadowOf(activity).nextStartedActivity
        assertEquals(Intent.ACTION_VIEW, intent.action)
        assertEquals(UpdateActivity.APK_MIME, intent.type)
        assertEquals("content", intent.data?.scheme)
        assertEquals("${activity.packageName}.updates", intent.data?.authority)
        assertEquals(Intent.FLAG_GRANT_READ_URI_PERMISSION, intent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION)
        assertEquals(0, intent.flags and Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        assertEquals(intent.data, intent.clipData?.getItemAt(0)?.uri)
        val bytes = activity.contentResolver.openInputStream(intent.data!!)!!.use { it.readBytes() }
        assertArrayEquals(file.readBytes(), bytes)
    }
    @Test fun screenRecreationKeepsARevalidatedPreviewWithoutInstalling() {
        val (saved, file) = state(); create(saved)
        await { button(R.string.install_update).isEnabled }
        lifecycle.recreate(); activity = lifecycle.get()
        await { button(R.string.install_update).isEnabled }
        assertTrue(file.exists())
        assertTrue(hasText(R.string.update_ready))
        assertNull(shadowOf(activity).nextStartedActivity)
    }
    @Test fun changedFileNeverReachesTheInstallerAndNeverTouchesTheNotes() {
        val (saved, file) = state(); create(saved)
        await { button(R.string.install_update).isEnabled }
        val notes = File(activity.filesDir, "knowledge-nexus-default.json").apply { writeText("private notes unchanged") }
        file.writeText("changed APK bytes")
        shadowOf(activity.packageManager).setCanRequestPackageInstalls(true)
        button(R.string.install_update).performClick()
        await { hasText(R.string.update_changed) }
        assertFalse(button(R.string.install_update).isEnabled)
        assertNull(shadowOf(activity).nextStartedActivity)
        assertEquals("private notes unchanged", notes.readText())
    }
    @Test fun wrongCertificateCannotRestoreAnInstallablePreview() {
        val (saved, _) = state(wrongSigner = true); create(saved)
        await { hasText(R.string.update_wrong_signature) }
        assertFalse(button(R.string.install_update).isEnabled)
        assertNull(shadowOf(activity).nextStartedActivity)
    }
}
