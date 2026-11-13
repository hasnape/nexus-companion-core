package fr.nexuscompanion.android

import org.json.JSONObject
import org.json.JSONTokener

/** A deliberately small evidence budget, independent of the full personal library. */
internal data class LocalDraftEvidence(val key: String, val noteId: String, val sentences: List<String>) {
    init {
        require(key.matches(Regex("N[12]")) && noteId.length in 1..64)
        require(sentences.size in 1..3 && sentences.all { it.length in 8..300 && GroundedNotes.safeText(it) })
        require(sentences.sumOf { it.length } <= 420)
    }
}

internal object GroundedNotes {
    data class Quote(val source: String, val text: String)
    fun safeText(text: String) = !text.contains("<|") && !text.contains("|>") &&
        text.none { it.code < 32 && it !in "\n\r\t" }

    /** Only complete stored sentences enter the model. Do not silently truncate a negation. */
    fun prepare(notes: List<LocalKnowledgeLibrary.Entry>, terms: List<String>): List<LocalDraftEvidence> =
        notes.distinctBy { it.id }.take(2).mapIndexedNotNull { index, note ->
            val candidates = note.excerpt.split(Regex("(?<=[.!?])\\s+|[\\r\\n]+"))
                .map(String::trim).filter { it.length in 8..300 && safeText(it) }.distinct()
                .sortedByDescending { KnowledgePolicy.score(terms, "", it) }
            val selected = mutableListOf<String>()
            var count = 0
            for (sentence in candidates) {
                if (selected.size >= 3) break
                if (count + sentence.length > 420) continue
                selected.add(sentence); count += sentence.length
            }
            if (selected.isEmpty()) null else LocalDraftEvidence("N${index + 1}", note.id, selected)
        }

    /** The model chooses quotations; its own unverified prose is never accepted as evidence. */
    fun validate(output: String?, evidence: List<LocalDraftEvidence>): List<Quote>? = try {
        require(output != null && output.length in 2..4000 && evidence.isNotEmpty())
        val parser = JSONTokener(output)
        val json = parser.nextValue() as? JSONObject ?: error("Expected object")
        require(parser.nextClean() == '\u0000')
        require(json.keys().asSequence().toSet() == setOf("quotes"))
        val rows = json.getJSONArray("quotes")
        require(rows.length() in 1..2)
        val allowed = evidence.associateBy { it.key }
        val quotes = (0 until rows.length()).map { i ->
            val row = rows.getJSONObject(i)
            require(row.keys().asSequence().toSet() == setOf("source", "text"))
            val key = row.getString("source")
            val text = row.getString("text").trim()
            require(allowed[key]?.sentences?.contains(text) == true)
            Quote(key, text)
        }.distinct()
        require(quotes.sumOf { it.text.length } <= 600)
        quotes
    } catch (_: Exception) { null }

    fun prompt(evidence: List<LocalDraftEvidence>): String = evidence.joinToString("\n\n") {
        "SOURCE ${it.key}:\n" + it.sentences.joinToString("\n")
    }
}
