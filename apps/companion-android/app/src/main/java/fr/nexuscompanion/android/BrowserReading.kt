package fr.nexuscompanion.android

import android.net.Uri
import java.text.Normalizer
import java.util.Locale

/** An explicitly requested external reading. Its text is never a user command. */
internal class BrowserReading private constructor(val title: String, val text: String, val source: String) {
    companion object {
        fun copied(text: String): BrowserReading? {
            val clean = text.trim()
            if (clean.isBlank() || clean.length > NativeCapacity.MAX_NOTE_CHARS ||
                clean.matches(Regex("https?://\\S+"))) return null
            return BrowserReading("Texte copié", clean, "external:clipboard")
        }

        fun chrome(text: String, page: ChromeAiText.Page): BrowserReading? {
            if (text.isBlank() || text.length > NativeCapacity.MAX_NOTE_CHARS) return null
            var end = minOf(page.query.length, 110)
            if (end > 0 && page.query[end - 1].isHighSurrogate()) end--
            return BrowserReading("Réponse IA Chrome — ${page.query.take(end)}", text, page.source)
        }
    }
}

/** Deliberately conservative: never label ordinary search snippets as an AI answer. */
internal object ChromeAiText {
    data class Block(val text: String, val heading: Boolean = false, val control: Boolean = false)
    data class Page(val query: String, val source: String)
    data class Snapshot(val address: String?, val blocks: List<Block>)
    const val MAX_BLOCKS = 400
    const val MAX_CAPTURE_CHARS = 48_000
    private val hosts = setOf("google.com", "www.google.com", "google.fr", "www.google.fr")
    private val markers = setOf(
        "ai overview", "ai overviews", "apercu ia", "apercus ia", "apercu cree par ia", "apercu cree par l ia",
        "apercu genere par ia", "apercu genere par l ia", "resume genere par ia", "resume genere par l ia"
    )
    private val boundaries = setOf(
        "resultats de recherche", "resultats web", "tous les resultats", "autres questions", "les internautes demandent aussi",
        "recherches associees", "videos", "images", "search results", "web results", "people also ask", "related searches"
    )
    private val controls = setOf(
        "afficher plus", "tout afficher", "voir plus", "en savoir plus", "sources", "ecouter", "partager", "copier",
        "show more", "learn more", "listen", "share", "copy", "utile", "inutile", "helpful", "not helpful"
    )
    private val loading = setOf("generation en cours", "recherche en cours", "generating", "thinking", "reflexion en cours")
    internal fun words(text: String): String = Normalizer.normalize(text, Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "").lowercase(Locale.ROOT).replace(Regex("[^\\p{L}\\p{N}]+"), " ").trim()

    fun page(address: String?): Page? {
        val raw = address?.trim()?.takeIf { it.isNotEmpty() && it.length <= 2048 && it.none(Char::isISOControl) } ?: return null
        val uri = Uri.parse(if (raw.startsWith("https://")) raw else "https://$raw")
        if (uri.scheme != "https" || uri.host !in hosts || uri.userInfo != null || uri.port !in setOf(-1, 443) ||
            uri.path != "/search" || '\\' in raw) return null
        val query = uri.getQueryParameter("q")?.trim()?.takeIf { it.isNotEmpty() && it.length <= 300 && it.none(Char::isISOControl) }
            ?: return null
        val source = Uri.Builder().scheme("https").authority(uri.host).path("/search").appendQueryParameter("q", query).build().toString()
        return Page(query, if (source.length <= 512) source else "https://${uri.host}/search")
    }

    fun extract(snapshot: Snapshot, expectedQuery: String?): BrowserReading? {
        val page = page(snapshot.address) ?: return null
        fun queryKey(value: String) = Normalizer.normalize(value, Normalizer.Form.NFC).trim().replace(Regex("\\s+"), " ").lowercase(Locale.ROOT)
        if (expectedQuery != null && queryKey(page.query) != queryKey(expectedQuery)) return null
        if (snapshot.blocks.size > MAX_BLOCKS || snapshot.blocks.sumOf { it.text.length } > MAX_CAPTURE_CHARS) return null
        var started = false
        val lines = mutableListOf<String>()
        val seen = mutableSetOf<String>()
        var chars = 0
        for (block in snapshot.blocks) {
            for (line in block.text.lineSequence().map(String::trim).filter(String::isNotEmpty)) {
                val normalized = words(line)
                if (!started) {
                    // A navigation tab "Mode IA" alone does not prove that an answer is present.
                    if (!block.control && normalized in markers) started = true
                    continue
                }
                if (normalized in loading) return null
                if (normalized in boundaries) return makeReading(lines, page)
                if (block.control || normalized in markers || normalized in controls || !seen.add(normalized)) continue
                if (chars + line.length + 1 > NativeCapacity.MAX_NOTE_CHARS) return makeReading(lines, page)
                lines.add(line)
                chars += line.length + 1
            }
        }
        return makeReading(lines, page)
    }

    private fun makeReading(lines: List<String>, page: Page): BrowserReading? {
        val text = lines.joinToString("\n")
        return if (text.length >= 3 && text.any(Char::isLetterOrDigit)) BrowserReading.chrome(text, page) else null
    }

    fun isReadCommand(message: String): Boolean = VoiceTranscriptPolicy.commandWords(message).replaceFirst(
        Regex("^(?:peux tu|tu peux|pourrais tu) (?:me )?"), ""
    ).replaceFirst(Regex("^lire "), "lis ").replaceFirst(Regex("^lis moi "), "lis ") in setOf(
        "lis la reponse de chrome", "lis la reponse ia de chrome", "lis le resultat de chrome", "lis le resultat ia de chrome",
        "lis le resultat du navigateur chrome", "lis la reponse dans chrome", "lis la reponse ia", "lis le resultat ia",
        "lis la reponse du navigateur chrome"
    )
}
