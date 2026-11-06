package fr.nexuscompanion.android

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.InterruptedIOException
import java.security.MessageDigest
import java.util.UUID

internal data class PreparedUpdate(val token: String, val metadata: UpdatePackage, val sha256: String)

/** A private immutable snapshot, never a live reference to the external document provider. */
internal class LocalUpdateRepository(
    directory: File,
    private val installed: () -> UpdatePackage,
    private val archive: (File) -> UpdatePackage,
    private val sdk: Int,
    private val maxBytes: Long = 64L * 1024 * 1024,
    private val maxStagedBytes: Long = 128L * 1024 * 1024
) {
    // Android can expose the app data directory through a framework-owned path alias.
    private val directory = directory.canonicalFile
    private val tokenPattern = Regex("update-[a-f0-9-]{36}\\.apk")

    fun prepare(input: InputStream): PreparedUpdate {
        var target: File? = null
        var accepted = false
        try {
            val prepared = input.use { source ->
                if (!directory.isDirectory && !directory.mkdirs()) throw UpdateRejected(UpdateProblem.READ_FAILED)
                pruneExpired()
                val used = directory.listFiles().orEmpty().sumOf { it.length() }
                val file = File(directory, "update-${UUID.randomUUID()}.apk")
                target = file
                var copied = 0L
                val digest = MessageDigest.getInstance("SHA-256")
                FileOutputStream(file).use { output ->
                    val buffer = ByteArray(32 * 1024)
                    while (true) {
                        checkInterrupted()
                        val size = source.read(buffer)
                        if (size < 0) break
                        if (size == 0) throw UpdateRejected(UpdateProblem.READ_FAILED)
                        copied += size
                        if (copied > maxBytes || copied + used > maxStagedBytes) throw UpdateRejected(UpdateProblem.TOO_LARGE)
                        output.write(buffer, 0, size)
                        digest.update(buffer, 0, size)
                    }
                    output.flush(); output.fd.sync()
                }
                checkInterrupted()
                if (copied == 0L) throw UpdateRejected(UpdateProblem.INVALID)
                val metadata = archive(file)
                UpdatePackagePolicy.validate(installed(), metadata, sdk)
                if (!file.setReadOnly()) throw UpdateRejected(UpdateProblem.READ_FAILED)
                PreparedUpdate(file.name, metadata, hex(digest.digest()))
            }
            accepted = true
            return prepared
        } finally { if (!accepted) target?.delete() }
    }

    /** Recheck after screen recreation or permission settings, before handing bytes to Android. */
    fun restore(token: String, expectedSha256: String): PreparedUpdate {
        val file = file(token)
        if (!Regex("[a-f0-9]{64}").matches(expectedSha256) || !file.isFile || file.length() !in 1..maxBytes) {
            throw UpdateRejected(UpdateProblem.INVALID)
        }
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(32 * 1024)
            var total = 0L
            while (true) {
                checkInterrupted()
                val size = input.read(buffer)
                if (size < 0) break
                total += size
                if (total > maxBytes) throw UpdateRejected(UpdateProblem.TOO_LARGE)
                digest.update(buffer, 0, size)
            }
        }
        if (hex(digest.digest()) != expectedSha256) throw UpdateRejected(UpdateProblem.CHANGED)
        val metadata = archive(file)
        UpdatePackagePolicy.validate(installed(), metadata, sdk)
        return PreparedUpdate(token, metadata, expectedSha256)
    }

    fun file(token: String): File {
        if (!tokenPattern.matches(token)) throw UpdateRejected(UpdateProblem.INVALID)
        val file = File(directory, token).absoluteFile
        if (file.canonicalFile != file || file.parentFile?.canonicalFile != directory.canonicalFile) {
            throw UpdateRejected(UpdateProblem.INVALID)
        }
        return file
    }

    fun discard(token: String) { file(token).delete() }

    private fun pruneExpired() {
        val cutoff = System.currentTimeMillis() - 24 * 60 * 60 * 1000L
        directory.listFiles().orEmpty().filter { tokenPattern.matches(it.name) && it.lastModified() < cutoff }.forEach { it.delete() }
    }

    private fun checkInterrupted() { if (Thread.currentThread().isInterrupted) throw InterruptedIOException("Update import canceled") }

    companion object {
        private fun hex(bytes: ByteArray) = bytes.joinToString("") { "%02x".format(it.toInt() and 255) }

        @Suppress("DEPRECATION")
        fun android(context: Context): LocalUpdateRepository {
            val manager = context.packageManager
            fun metadata(info: PackageInfo?): UpdatePackage {
                val value = info ?: throw UpdateRejected(UpdateProblem.INVALID)
                val signers = value.signingInfo?.apkContentsSigners.orEmpty().map {
                    hex(MessageDigest.getInstance("SHA-256").digest(it.toByteArray()))
                }.toSet()
                return UpdatePackage(value.packageName, value.longVersionCode,
                    value.versionName.orEmpty().take(80).ifBlank { value.longVersionCode.toString() }, signers,
                    value.applicationInfo?.minSdkVersion ?: 0)
            }
            return LocalUpdateRepository(File(context.cacheDir, "updates"),
                { metadata(manager.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)) },
                { file -> metadata(manager.getPackageArchiveInfo(file.absolutePath, PackageManager.GET_SIGNING_CERTIFICATES)) },
                Build.VERSION.SDK_INT)
        }
    }
}
