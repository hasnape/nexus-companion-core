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

    /**
     * Conservative defense-in-depth, not a guarantee that free text is harmless.
     * Owner must still review the full note before sharing.
     */
    fun safeToShare(title: String, body: String): Boolean {
        val text = "$title $body"
        if (text.length > 800) return false
        val unsafe = listOf(
            Regex("(?i)\\b(?:mot de passe|password|code secret|code pin|identifiant|token|api.key|cle privee|secret|iban|bic|carte bancaire|cvv|sante|medical|diagnostic|adresse personnelle|domicile)\\b"),
            Regex("[0-9]{4,}"),
            // French phone numbers are often written as five two-digit groups.
            // The four-consecutive-digits rule above does not catch those.
            // Include Unicode spaces (notably the narrow no-break space on Android).
            Regex("""(?<!\d)(?:0[1-9]|\+33[\s\p{Zs}.-]?[1-9]|0033[\s\p{Zs}.-]?[1-9])(?:[\s\p{Zs}.-]?\d{2}){4}(?!\d)"""),
            Regex("[\\w.+-]+@[\\w.-]+\\.[A-Za-z]{2,}"),
            Regex("(?i)https?://"),
            Regex("(?i)\\b(?:rue|avenue|boulevard|impasse|allee|chemin)\\b")
        )
        return unsafe.none { it.containsMatchIn(text) }
    }

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
