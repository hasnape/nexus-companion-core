package fr.nexuscompanion.android

/**
 * Access policy for conversational memory.
 *
 * Existing/legacy notes remain private by default. Only a note explicitly
 * marked for conversational sharing by the owner may be read while locked.
 * No automatic content-based declassification or secret-pattern guessing.
 */
internal object SelectiveMemoryAccess {
    const val SHARED_SOURCE = "user:conversation-shared"

    fun canRead(note: LocalKnowledgeLibrary.Entry, unlocked: Boolean): Boolean =
        unlocked || note.source == SHARED_SOURCE

    fun accessible(
        notes: List<LocalKnowledgeLibrary.Entry>,
        unlocked: Boolean
    ): List<LocalKnowledgeLibrary.Entry> = notes.filter { canRead(it, unlocked) }

    fun needsUnlock(
        notes: List<LocalKnowledgeLibrary.Entry>,
        unlocked: Boolean
    ): Boolean = !unlocked && notes.any { !canRead(it, false) }
}
