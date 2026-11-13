package fr.nexuscompanion.android

import android.content.Context
import android.os.SystemClock

/** Existing reasoning suites run in an explicitly authorized, stable device fixture. */
internal val balancedTestDevice = DeviceResources.Snapshot(4, 4L shl 30, 1L shl 30,
    256L shl 20, 8L shl 30, false, false, 0, 80, true)
internal fun authorizedBrain(context: Context, library: LocalKnowledgeLibrary,
    elapsed: () -> Long = SystemClock::elapsedRealtime,
    search: (String) -> WikipediaKnowledgeSearch.Article? = { WikipediaKnowledgeSearch.search(context, it) }
) = NativeBrainService(context, library, elapsed, privateAccess = { true }, resources = { balancedTestDevice }, search = search)
