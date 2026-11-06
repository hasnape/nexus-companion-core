package fr.nexuscompanion.android

internal data class UpdatePackage(
    val packageName: String,
    val versionCode: Long,
    val versionName: String,
    val signers: Set<String>,
    val minSdk: Int
)

internal enum class UpdateProblem { INVALID, WRONG_APP, NOT_NEWER, SIGNATURE, ANDROID_VERSION, TOO_LARGE, CHANGED, READ_FAILED }
internal class UpdateRejected(val problem: UpdateProblem) : Exception(problem.name)

/** Conservative self-update policy. Key rotation needs a separate, reviewed migration. */
internal object UpdatePackagePolicy {
    fun validate(installed: UpdatePackage, candidate: UpdatePackage, sdk: Int) {
        if (candidate.packageName != installed.packageName) throw UpdateRejected(UpdateProblem.WRONG_APP)
        if (candidate.versionCode <= installed.versionCode) throw UpdateRejected(UpdateProblem.NOT_NEWER)
        val digest = Regex("[a-f0-9]{64}")
        if (installed.signers.isEmpty() || candidate.signers.isEmpty() ||
            (installed.signers + candidate.signers).any { !digest.matches(it) } || candidate.signers != installed.signers) {
            throw UpdateRejected(UpdateProblem.SIGNATURE)
        }
        if (candidate.minSdk < 1) throw UpdateRejected(UpdateProblem.INVALID)
        if (candidate.minSdk > sdk) throw UpdateRejected(UpdateProblem.ANDROID_VERSION)
    }
}
