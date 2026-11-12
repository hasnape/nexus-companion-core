package fr.nexuscompanion.android

import android.content.Context
import android.net.ConnectivityManager
import android.net.Uri
import android.os.SystemClock
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.net.URL
import javax.net.ssl.HttpsURLConnection

/** Only an explicitly requested topic reaches this HTTPS client. */
internal object WikipediaKnowledgeSearch {
    private const val HOST = "fr.wikipedia.org"
    private const val MAX_RESPONSE_CHARS = 48_000
    private const val TIMEOUT_MS = 3_500

    data class Article(val pageId: Int, val title: String, val extract: String) {
        val url: String get() = "https://$HOST/?curid=$pageId"
    }

    fun search(context: Context, topic: String): Article? {
        val searchText = WikipediaAnswerPolicy.searchText(topic)
        if (searchText.isBlank()) return null
        if (context.getSystemService(ConnectivityManager::class.java)?.activeNetwork == null) {
            throw IOException("Network unavailable")
        }
        val endpoint = Uri.Builder().scheme("https").authority(HOST).path("/w/api.php")
            .appendQueryParameter("action", "query")
            .appendQueryParameter("format", "json")
            .appendQueryParameter("formatversion", "2")
            .appendQueryParameter("generator", "search")
            .appendQueryParameter("gsrsearch", searchText)
            .appendQueryParameter("gsrnamespace", "0")
            .appendQueryParameter("gsrlimit", "5")
            .appendQueryParameter("prop", "extracts|pageprops")
            .appendQueryParameter("ppprop", "disambiguation")
            .appendQueryParameter("exintro", "1")
            .appendQueryParameter("explaintext", "1")
            .appendQueryParameter("exchars", "1200")
            .appendQueryParameter("exlimit", "5")
            .build()
        val connection = URL(endpoint.toString()).openConnection() as HttpsURLConnection
        try {
            connection.instanceFollowRedirects = false
            connection.useCaches = false
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("User-Agent",
                "NexusCompanion/${BuildConfig.VERSION_NAME} (https://github.com/hasnape/nexus-companion-core)")
            if (connection.responseCode != 200) throw IOException("Wikipedia request failed")

            val response = StringBuilder()
            val deadline = SystemClock.elapsedRealtime() + TIMEOUT_MS
            connection.inputStream.bufferedReader(Charsets.UTF_8).use { reader ->
                val buffer = CharArray(2048)
                while (true) {
                    if (SystemClock.elapsedRealtime() >= deadline) throw IOException("Wikipedia response timed out")
                    val count = reader.read(buffer)
                    if (count < 0) break
                    if (response.length + count > MAX_RESPONSE_CHARS) throw IOException("Wikipedia response too large")
                    response.append(buffer, 0, count)
                }
            }
            return readArticle(JSONObject(response.toString()), topic)
        } catch (invalid: JSONException) {
            throw IOException("Invalid Wikipedia response", invalid)
        } finally {
            connection.disconnect()
        }
    }

    internal fun readArticle(response: JSONObject, topic: String = ""): Article? {
        if (response.has("error")) throw IOException("Wikipedia returned an API error")
        val pages = response.optJSONObject("query")?.optJSONArray("pages") ?: return null
        return (0 until minOf(pages.length(), 10)).mapNotNull(pages::optJSONObject).mapNotNull { page ->
            if (page.optJSONObject("pageprops")?.has("disambiguation") == true) return@mapNotNull null
            val id = page.optInt("pageid")
            val title = WikipediaAnswerPolicy.clip(page.optString("title").trim(), 160)
            val extract = WikipediaAnswerPolicy.clip(page.optString("extract").trim(), 1800)
            if (id <= 0 || title.isBlank() || extract.isBlank()) return@mapNotNull null
            val relevance = WikipediaAnswerPolicy.relevance(topic, title, extract)
            if (relevance == 0) return@mapNotNull null
            Triple(Article(id, title, extract), relevance, page.optInt("index", Int.MAX_VALUE))
        }.sortedWith(compareByDescending<Triple<Article, Int, Int>> { it.second }.thenBy { it.third }).firstOrNull()?.first
    }
}
