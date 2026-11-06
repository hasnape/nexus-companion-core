package fr.nexuscompanion.android

import android.content.ContextWrapper
import android.content.pm.PackageInfo
import android.content.pm.Signature
import android.content.pm.SigningInfo
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadow.api.Shadow
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class InstallationDiagnosticTest {
    private val context get() = object : ContextWrapper(RuntimeEnvironment.getApplication()) {
        override fun getPackageName() = "fr.nexuscompanion.android.preview"
    }
    private fun install(name: String, signer: String) {
        val signing = Shadow.newInstanceOf(SigningInfo::class.java).apply { shadowOf(this).setSignatures(arrayOf(Signature(signer))) }
        shadowOf(context.packageManager).installPackage(PackageInfo().apply {
            packageName = name; setLongVersionCode(7); versionName = "test-version"; signingInfo = signing
        })
    }
    @Test fun identicalSignaturesAreReportedWithoutReadingTheNotes() {
        install(context.packageName, "0102"); install(InstallationDiagnostic.ORIGINAL_PACKAGE, "0102")
        File(context.filesDir, "knowledge-nexus-default.json").writeText("private-note-canary")
        val snapshot = InstallationDiagnostic.read(context)
        assertEquals(true, snapshot.signaturesMatch)
        assertTrue(snapshot.originalVersion!!.contains("test-version"))
        assertTrue(snapshot.details.contains("Android API : 35"))
        assertFalse(snapshot.details.contains("private-note-canary"))
    }
    @Test fun differentInstalledSignerIsVisibleWithoutClaimingAnUpgradeSucceeded() {
        install(context.packageName, "0102"); install(InstallationDiagnostic.ORIGINAL_PACKAGE, "0304")
        assertEquals(false, InstallationDiagnostic.read(context).signaturesMatch)
    }
    @Test fun missingOriginalInstallationIsAnUnknownComparison() {
        install(context.packageName, "0102")
        shadowOf(context.packageManager).removePackage(InstallationDiagnostic.ORIGINAL_PACKAGE)
        val snapshot = InstallationDiagnostic.read(context)
        assertNull(snapshot.originalVersion); assertNull(snapshot.signaturesMatch)
    }
}
