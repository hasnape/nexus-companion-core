package fr.nexuscompanion.android

/** Only explicit manual address fields are eligible; never guess a place from general prose. */
internal object SavedPlaceResolver {
    data class Match(val target: String, val evidence: List<LocalKnowledgeLibrary.Entry>)

    fun resolve(place: ExternalAppPolicy.Place, notes: List<LocalKnowledgeLibrary.Entry>, now: Long): List<Match> {
        val home = place == ExternalAppPolicy.Place.HOME
        val titles = if (home) setOf("adresse maison", "adresse domicile", "mon adresse", "adresse de mon domicile")
            else setOf("adresse travail", "adresse professionnelle", "adresse de mon travail")
        val prefix = if (home) Regex("^(?:(?:mon )?adresse(?: (?:domicile|maison|de mon domicile))?\\s*[:=]\\s*|mon adresse est\\s+)(.+)$", RegexOption.IGNORE_CASE)
            else Regex("^(?:adresse (?:travail|professionnelle|de mon travail)\\s*[:=]\\s*|mon adresse professionnelle est\\s+)(.+)$", RegexOption.IGNORE_CASE)
        val candidates = notes.mapNotNull { note ->
            if (note.source != "user:manual" || note.supersededBy != null || KnowledgePolicy.isExpired(note.expiresAt, now)) return@mapNotNull null
            val text = note.excerpt.trim()
            val target = prefix.matchEntire(text)?.groupValues?.get(1)?.trim()
                ?: text.takeIf { VoiceTranscriptPolicy.commandWords(note.title) in titles }
                ?: return@mapNotNull null
            if (target.length !in 2..300 || target.any { it.isISOControl() } ||
                Regex("https?:|@|\\b(?:mot de passe|password|iban|retiens|ouvre|ignore|supprime|envoie|instruction)\\b", RegexOption.IGNORE_CASE).containsMatchIn(target)) return@mapNotNull null
            note to target
        }
        return candidates.groupBy { KnowledgePolicy.noteKey(it.second) }.values.map { group -> Match(group.first().second, group.map { it.first }) }
    }
}
