package fr.nexuscompanion.android

import android.content.ComponentName
import android.content.pm.PackageManager
import androidx.core.content.FileProvider
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class UpdateFileProviderTest {
    @Before fun attachProviderForThisAppDirectory() {
        Robolectric.buildContentProvider(UpdateFileProvider::class.java).create()
    }
    @Test fun providerAndUpdateScreenAreNotExported() {
        val app = RuntimeEnvironment.getApplication()
        val info = app.packageManager.getProviderInfo(ComponentName(app, UpdateFileProvider::class.java), PackageManager.GET_META_DATA)
        assertFalse(info.exported); assertTrue(info.grantUriPermissions)
        assertFalse(app.packageManager.getActivityInfo(ComponentName(app, UpdateActivity::class.java), 0).exported)
    }
    @Test fun privateNotesCanNeverBecomeAnUpdateUri() {
        val app = RuntimeEnvironment.getApplication()
        val notes = File(app.filesDir, "knowledge.json").apply { writeText("private") }
        try { FileProvider.getUriForFile(app, "${app.packageName}.updates", notes); fail("Notes must be outside provider roots") }
        catch (_: IllegalArgumentException) { }
        assertEquals("private", notes.readText())
    }
    @Test fun grantedUpdateFilesCannotBeOpenedForWriting() {
        val app = RuntimeEnvironment.getApplication()
        val dir = File(app.cacheDir, "updates").apply { mkdirs() }
        val file = File(dir, "test.apk").apply { writeText("APK") }
        val uri = FileProvider.getUriForFile(app, "${app.packageName}.updates", file)
        val provider = Robolectric.buildContentProvider(UpdateFileProvider::class.java).create().get()
        try { provider.openFile(uri, "rw"); fail("Write access must be rejected") }
        catch (_: SecurityException) { }
        provider.openFile(uri, "r").use { assertTrue(it.fileDescriptor.valid()) }
        assertEquals("APK", file.readText())
    }
}
