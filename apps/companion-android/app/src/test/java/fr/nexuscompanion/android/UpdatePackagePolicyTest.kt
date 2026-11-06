package fr.nexuscompanion.android

import org.junit.Assert.*
import org.junit.Test

class UpdatePackagePolicyTest {
    private val installed = UpdatePackage("fr.nexuscompanion.android", 6, "0.4.0", setOf("a".repeat(64)), 31)
    private val candidate = installed.copy(versionCode = 7, versionName = "0.4.1")
    private fun rejects(problem: UpdateProblem, value: UpdatePackage, current: UpdatePackage = installed) {
        try { UpdatePackagePolicy.validate(current, value, 35); fail("Expected $problem") }
        catch (failure: UpdateRejected) { assertEquals(problem, failure.problem) }
    }

    @Test fun newerVersionWithExactlyTheInstalledSignersIsAccepted() {
        UpdatePackagePolicy.validate(installed, candidate, 35)
    }
    @Test fun anotherApplicationIsRejected() { rejects(UpdateProblem.WRONG_APP, candidate.copy(packageName = "other.app")) }
    @Test fun equalAndOlderVersionsAreRejected() {
        for (code in listOf(6L, 5L, 0L, -1L)) rejects(UpdateProblem.NOT_NEWER, candidate.copy(versionCode = code))
    }
    @Test fun missingUnknownAndMalformedSignersFailClosed() {
        for (signers in listOf(emptySet(), setOf("b".repeat(64)), setOf("bad"))) {
            rejects(UpdateProblem.SIGNATURE, candidate.copy(signers = signers))
        }
        rejects(UpdateProblem.SIGNATURE, candidate, installed.copy(signers = emptySet()))
    }
    @Test fun addingOrRemovingASignerDoesNotCountAsTheSameIdentity() {
        val both = setOf("a".repeat(64), "b".repeat(64))
        rejects(UpdateProblem.SIGNATURE, candidate.copy(signers = both))
        rejects(UpdateProblem.SIGNATURE, candidate, installed.copy(signers = both))
        UpdatePackagePolicy.validate(installed.copy(signers = both), candidate.copy(signers = both.reversed().toSet()), 35)
    }
    @Test fun incompatibleAndroidVersionIsRejected() { rejects(UpdateProblem.ANDROID_VERSION, candidate.copy(minSdk = 36)) }
    @Test fun invalidMinimumSdkIsRejected() { rejects(UpdateProblem.INVALID, candidate.copy(minSdk = 0)) }
    @Test fun longVersionCodesAreNeverNarrowedToInt() {
        UpdatePackagePolicy.validate(installed.copy(versionCode = 3_000_000_000), candidate.copy(versionCode = 4_000_000_000), 35)
    }
}
