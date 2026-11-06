package fr.nexuscompanion.android

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.io.InterruptedIOException

class LocalUpdateRepositoryTest {
    @get:Rule val temporary = TemporaryFolder()
    private val installed = UpdatePackage("fr.nexuscompanion.android", 6, "0.4.0", setOf("a".repeat(64)), 31)
    private val candidate = installed.copy(versionCode = 7, versionName = "0.4.1")
    private class Stream(bytes: ByteArray, private val badClose: Boolean = false) : ByteArrayInputStream(bytes) {
        var closed = false
        override fun close() { closed = true; if (badClose) throw IOException("Provider closed badly"); super.close() }
    }
    private fun repo(dir: File, value: UpdatePackage = candidate, max: Long = 1024, quota: Long = 4096) =
        LocalUpdateRepository(dir, { installed }, { value }, 35, max, quota)
    private fun rejects(problem: UpdateProblem, operation: () -> Unit) {
        try { operation(); fail("Expected $problem") }
        catch (failure: UpdateRejected) { assertEquals(problem, failure.problem) }
    }

    @Test fun importedSnapshotIsIndependentOfTheSelectedDocumentAndCanBeRestored() {
        val dir = temporary.newFolder(); val repository = repo(dir)
        val bytes = "original APK bytes".toByteArray(); val stream = Stream(bytes)
        val prepared = repository.prepare(stream)
        bytes.fill(0)
        assertTrue(stream.closed)
        assertEquals("original APK bytes", repository.file(prepared.token).readText())
        assertEquals(prepared, repository.restore(prepared.token, prepared.sha256))
    }
    @Test fun oversizedImportClosesTheSourceAndLeavesNoPartialFile() {
        val dir = temporary.newFolder(); val stream = Stream(ByteArray(1025))
        rejects(UpdateProblem.TOO_LARGE) { repo(dir).prepare(stream) }
        assertTrue(stream.closed); assertTrue(dir.listFiles().orEmpty().isEmpty())
    }
    @Test fun totalStagingQuotaIsEnforced() {
        val dir = temporary.newFolder(); val repository = repo(dir, max = 10, quota = 12)
        val first = repository.prepare(Stream(ByteArray(8)))
        rejects(UpdateProblem.TOO_LARGE) { repository.prepare(Stream(ByteArray(8))) }
        assertEquals(listOf(first.token), dir.listFiles().orEmpty().map { it.name })
    }
    @Test fun emptyAndInvalidPackagesAreRemoved() {
        val dir = temporary.newFolder()
        rejects(UpdateProblem.INVALID) { repo(dir).prepare(Stream(byteArrayOf())) }
        rejects(UpdateProblem.WRONG_APP) { repo(dir, candidate.copy(packageName = "other.app")).prepare(Stream(byteArrayOf(1))) }
        assertTrue(dir.listFiles().orEmpty().isEmpty())
    }
    @Test fun readAndCloseFailuresNeverLeaveAnAcceptedSnapshot() {
        val dir = temporary.newFolder()
        val badRead = object : ByteArrayInputStream(byteArrayOf(1)) {
            override fun read(buffer: ByteArray, offset: Int, length: Int): Int = throw IOException("Provider unavailable")
        }
        for (input in listOf(badRead, Stream(byteArrayOf(1), badClose = true))) {
            try { repo(dir).prepare(input); fail("Expected I/O failure") } catch (_: IOException) { }
            assertTrue(dir.listFiles().orEmpty().isEmpty())
        }
    }
    @Test fun cancellationClosesTheSourceAndRemovesThePartialFile() {
        val dir = temporary.newFolder(); val stream = Stream(byteArrayOf(1))
        try {
            Thread.currentThread().interrupt()
            try { repo(dir).prepare(stream); fail("Expected cancellation") } catch (_: InterruptedIOException) { }
        } finally { Thread.interrupted() }
        assertTrue(stream.closed); assertTrue(dir.listFiles().orEmpty().isEmpty())
    }
    @Test fun changedSnapshotIsRejectedBeforeInstallation() {
        val dir = temporary.newFolder(); val repository = repo(dir)
        val prepared = repository.prepare(Stream("one".toByteArray()))
        repository.file(prepared.token).apply { setWritable(true); writeText("two") }
        rejects(UpdateProblem.CHANGED) { repository.restore(prepared.token, prepared.sha256) }
    }
    @Test fun pathTraversalAndLinksCannotReadOrDeletePrivateFiles() {
        val dir = temporary.newFolder("updates"); val private = temporary.newFile("notes.json").apply { writeText("private") }
        val repository = repo(dir)
        for (token in listOf("../notes.json", private.absolutePath, "update-../../notes.json.apk")) {
            rejects(UpdateProblem.INVALID) { repository.discard(token) }
        }
        val link = File(dir, "update-00000000-0000-0000-0000-000000000001.apk")
        java.nio.file.Files.createSymbolicLink(link.toPath(), private.toPath())
        rejects(UpdateProblem.INVALID) { repository.restore(link.name, "a".repeat(64)) }
        assertEquals("private", private.readText())
    }
    @Test fun missingOrMalformedRestorationDataFailsClosed() {
        val repository = repo(temporary.newFolder())
        rejects(UpdateProblem.INVALID) { repository.restore("update-00000000-0000-0000-0000-000000000001.apk", "a".repeat(64)) }
        val prepared = repository.prepare(Stream(byteArrayOf(1)))
        rejects(UpdateProblem.INVALID) { repository.restore(prepared.token, "not-a-hash") }
    }
    @Test fun obsoleteStagingFilesArePrunedWithoutTouchingNotes() {
        val dir = temporary.newFolder("updates")
        val old = File(dir, "update-00000000-0000-0000-0000-000000000001.apk").apply {
            writeText("old"); setLastModified(System.currentTimeMillis() - 2 * 24 * 60 * 60 * 1000L)
        }
        val private = temporary.newFile("knowledge.json").apply { writeText("private") }
        repo(dir).prepare(Stream(byteArrayOf(1)))
        assertFalse(old.exists()); assertEquals("private", private.readText())
    }
    @Test fun installedVersionIsRecheckedAfterThePreview() {
        val dir = temporary.newFolder(); var current = installed
        val repository = LocalUpdateRepository(dir, { current }, { candidate }, 35)
        val prepared = repository.prepare(Stream(byteArrayOf(1)))
        current = candidate
        rejects(UpdateProblem.NOT_NEWER) { repository.restore(prepared.token, prepared.sha256) }
    }
    @Test fun androidDirectoryAliasesWorkWhileLeafSymlinksRemainForbidden() {
        val actual = temporary.newFolder("real-cache")
        val alias = File(temporary.root, "cache-alias")
        java.nio.file.Files.createSymbolicLink(alias.toPath(), actual.toPath())
        val repository = repo(alias)
        val prepared = repository.prepare(Stream(byteArrayOf(1, 2, 3)))
        assertEquals(prepared, repository.restore(prepared.token, prepared.sha256))
        assertEquals(actual.canonicalFile, repository.file(prepared.token).parentFile)
    }
}
