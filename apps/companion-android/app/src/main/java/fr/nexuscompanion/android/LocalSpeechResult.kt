package fr.nexuscompanion.android

import org.json.JSONObject

/** Vosk likelihoods are ranking scores, not probabilities. Keep them separate. */
internal object LocalSpeechResult {
    fun hypotheses(payload: String): List<SpeechHypothesis> {
        require(payload.length <= NativeCapacity.MAX_SPEECH_JSON_CHARS)
        val root = JSONObject(payload)
        val alternatives = root.optJSONArray("alternatives")
        if (alternatives != null && alternatives.optJSONObject(0) == null) return emptyList()
        val entries = if (alternatives == null) listOf(root) else (0 until minOf(3, alternatives.length())).mapNotNull(alternatives::optJSONObject)
        val bestText = entries.firstOrNull()?.optString("text")?.trim().orEmpty()
        if (bestText.isEmpty() || bestText.length > NativeCapacity.MAX_MESSAGE_CHARS) return emptyList()
        return entries.mapNotNull { entry ->
            val text = entry.optString("text").trim()
            if (text.isEmpty() || text.length > NativeCapacity.MAX_MESSAGE_CHARS) return@mapNotNull null
            val words = entry.optJSONArray("result")
            val confidence = (0 until (words?.length() ?: 0)).mapNotNull { i ->
                words?.optJSONObject(i)?.optDouble("conf", Double.NaN)?.takeIf { it.isFinite() && it in 0.0..1.0 }
            }.takeIf { it.isNotEmpty() }?.average()?.toFloat()
            val likelihood = if (alternatives == null) null else entry.optDouble("confidence", Double.NaN).takeIf { it.isFinite() }
            SpeechHypothesis(text, confidence, likelihood)
        }.distinctBy { it.text }
    }
}
