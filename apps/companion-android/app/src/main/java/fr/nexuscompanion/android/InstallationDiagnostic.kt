package fr.nexuscompanion.android

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import java.security.MessageDigest

internal data class InstallationSnapshot(val originalVersion: String?, val signaturesMatch: Boolean?, val details: String)

internal object InstallationDiagnostic {
    const val ORIGINAL_PACKAGE = "fr.nexuscompanion.android"

    @Suppress("DEPRECATION")
    fun read(context: Context): InstallationSnapshot {
        fun info(name: String) = try { context.packageManager.getPackageInfo(name, PackageManager.GET_SIGNING_CERTIFICATES) }
            catch (_: PackageManager.NameNotFoundException) { null }
        fun signatures(name: String) = info(name)?.signingInfo?.apkContentsSigners.orEmpty().map {
            MessageDigest.getInstance("SHA-256").digest(it.toByteArray()).joinToString("") { byte -> "%02x".format(byte.toInt() and 255) }
        }.toSet()
        val current = signatures(context.packageName)
        val original = info(ORIGINAL_PACKAGE)
        val originalSigners = if (original == null) emptySet() else signatures(ORIGINAL_PACKAGE)
        val match = if (current.isEmpty() || originalSigners.isEmpty()) null else current == originalSigners
        val version = original?.let { "${it.versionName.orEmpty()} (${it.longVersionCode})" }
        val details = listOf("Nexus ${BuildConfig.VERSION_NAME}", "Application : ${context.packageName}",
            "Android API : ${Build.VERSION.SDK_INT}", "Nexus d'origine : ${version ?: "absent"}",
            "Signature de ce fichier : ${current.sorted().joinToString().ifBlank { "indisponible" }}",
            "Signature d'origine : ${originalSigners.sorted().joinToString().ifBlank { "indisponible" }}").joinToString("\n")
        return InstallationSnapshot(version, match, details)
    }
}
