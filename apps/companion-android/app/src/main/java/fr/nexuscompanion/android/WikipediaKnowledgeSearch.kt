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
    private const val MAX_RESPONSE_CHARS = 24_000
    private const val TIMEOUT_MS = 3_500

    data class Article(val pageId: Int, val title: String, val extract: String) {
        val url: String get() = "https://$HOST/?curid=$pageId"
    }

    fun search(context: Context, topic: String): Article? {
        if (context.getSystemService(ConnectivityManager::class.java)?.activeNetwork == null) {
            throw IOException("Network unavailable")
        }
        val endpoint = Uri.Builder().scheme("https").authority(HOST).path("/w/api.php")
            .appendQueryParameter("action", "query")
            .appendQueryParameter("format", "json")
            .appendQueryParameter("formatversion", "2")
            .appendQueryParameter("generator", "search")
            .appendQueryParameter("gsrsearch", topic)
            .appendQueryParameter("gsrnamespace", "0")
            .appendQueryParameter("gsrlimit", "3")
            .appendQueryParameter("prop", "extracts|pageprops")
            .appendQueryParameter("ppprop", "disambiguation")
            .appendQueryParameter("exintro", "1")
            .appendQueryParameter("explaintext", "1")
            .appendQueryParameter("exchars", "550")
            .appendQueryParameter("exlimit", "3")
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
            return readArticle(JSONObject(response.toString()))
        } catch (invalid: JSONException) {
            throw IOException("Invalid Wikipedia response", invalid)
        } finally {
            connection.disconnect()
        }
    }

    internal fun readArticle(response: JSONObject): Article? {
        if (response.has("error")) throw IOException("Wikipedia returned an API error")
        val pages = response.optJSONObject("query")?.optJSONArray("pages") ?: return null
        val first = (0 until pages.length()).mapNotNull(pages::optJSONObject)
            .minByOrNull { it.optInt("index", Int.MAX_VALUE) } ?: return null
        if (first.optJSONObject("pageprops")?.has("disambiguation") == true) return null
        val id = first.optInt("pageid")
        val title = first.optString("title").trim().take(160)
        val extract = first.optString("extract").trim().take(600)
        return if (id > 0 && title.isNotBlank() && extract.isNotBlank()) Article(id, title, extract) else null
    }
}
