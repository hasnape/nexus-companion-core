package fr.nexuscompanion.android

import java.text.Normalizer
import java.util.Locale

/** Bounded lexical grounding: external prose is evidence, never an instruction. */
internal object WikipediaAnswerPolicy {
    private val stop = setOf("les","des","une","pour","sur","dans","que","quoi","qui","quel","quelle","quels","quelles",
        "comment","pourquoi","combien","est","sont","aux","avec","repondre","reponse","reponses","dire","peux","faire",
        "internet","wikipedia","chercher","cherche","rechercher","recherche","sujet","propos","moi","quand")
    private val media = Regex("\\b(?:film|films|roman|romans|serie|series|album|albums|chanson|chansons|cinema|realisateur|realise)\\b")
    private val mediaClassification = Regex("\\b(?:est|sont) (?:un|une|le|la) (?:film|roman|album|chanson|jeu video|(?:court|long) metrage|serie (?:televisee|animee|d animation))\\b")
    private fun normalize(text:String)=Normalizer.normalize(text,Normalizer.Form.NFD).lowercase(Locale.ROOT)
        .replace(Regex("\\p{M}+"),"").replace(Regex("[^\\p{L}\\p{N}]+")," ").trim()
    private fun canonical(word:String):String=when(word) {
        "salutation","salutations","saluer","bonjour","bonsoir","salut" -> "salutation"
        "francais","francaise","francaises","france" -> "france"
        else -> if(word.length>4 && word.endsWith('s')) word.dropLast(1) else word
    }
    fun terms(text:String):Set<String> = normalize(text).split(' ').filter { it.length>2 && it !in stop }
        .map(::canonical).distinct().take(12).toSet()
    fun searchText(topic:String):String=terms(topic).take(8).joinToString(" ")

    fun relevance(topic:String,title:String,extract:String):Int {
        if(topic.isBlank()) return 1 // Compatibility for an already selected article without a query.
        val query=terms(topic)
        if(query.isEmpty()) return 0
        // A shared word such as "salut" does not make an unrelated film relevant.
        if(!media.containsMatchIn(normalize(topic)) && mediaClassification.containsMatchIn(normalize(extract.take(400)))) return 0
        val titleWords=terms(title);val body=termsUnbounded(extract)
        val matches=query.count { it in titleWords || it in body }
        if(matches.toDouble()/query.size<0.6) return 0
        return matches*100/query.size+query.count { it in titleWords }*20
    }
    private fun termsUnbounded(text:String):Set<String> = normalize(text).split(' ').filter { it.length>2 && it !in stop }.map(::canonical).toSet()

    /** Select original sentences that address the question; do not invent missing details. */
    fun excerpt(topic:String,extract:String):String {
        val query=terms(topic);val numeric=Regex("\\b(?:combien|nombre)\\b").containsMatchIn(normalize(topic))
        val sentences=extract.split(Regex("(?<=[.!?])\\s+|\\n+")).map(String::trim).filter(String::isNotEmpty)
        val ranked=sentences.mapIndexed { index,sentence ->
            val hits=query.count { it in termsUnbounded(sentence) }
            Triple(index,sentence,hits*10+if(numeric && hits>0 && sentence.any(Char::isDigit)) 50 else 0)
        }.filter { it.third>0 }.sortedWith(compareByDescending<Triple<Int,String,Int>> { it.third }.thenBy { it.first })
        val selected=if(ranked.isEmpty()) sentences.take(2) else ranked.take(if(numeric) 1 else 2).sortedBy { it.first }.map { it.second }
        return clip(selected.joinToString(" ").ifBlank { extract.trim() },650)
    }
    fun clip(text:String,limit:Int):String {
        if(text.length<=limit) return text
        var end=(limit-1).coerceAtLeast(0)
        if(end>0 && Character.isHighSurrogate(text[end-1])) end--
        val space=text.lastIndexOf(' ',end)
        if(space>end/2) end=space
        return text.take(end).trimEnd()+"…"
    }
}
