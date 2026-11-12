package fr.nexuscompanion.android

/** Lossless boundaries for an Android TTS engine's per-call UTF-16 limit. */
internal object SpeechText {
    fun split(text: String, limit: Int): List<String> {
        require(limit >= 2)
        val parts = mutableListOf<String>()
        var start = 0
        while (start < text.length) {
            var end = minOf(start + limit, text.length)
            if (end < text.length) {
                val minimum = start + limit / 2
                val sentence = (end - 1 downTo minimum).firstOrNull { text[it] in ".!?;\n" }
                val space = (end - 1 downTo minimum).firstOrNull { text[it].isWhitespace() }
                end = sentence?.plus(1) ?: space?.plus(1) ?: end
                if (sentence != null) while (end < minOf(start + limit, text.length) && text[end].isWhitespace()) end++
                if (Character.isHighSurrogate(text[end - 1]) && Character.isLowSurrogate(text[end])) end--
            }
            check(end > start)
            parts.add(text.substring(start, end))
            start = end
        }
        return parts
    }
}
