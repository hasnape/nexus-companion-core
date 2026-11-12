package fr.nexuscompanion.android

/** One explicit, expiring request. No disk, intent extras, or unsolicited screen events. */
internal class ChromeReadingSession(private val elapsed: () -> Long) {
    data class Request(val id: Long, val expectedQuery: String?, val expires: Long)
    data class Result(val reading: BrowserReading?)
    private var serial = 0L
    private var request: Request? = null
    private var result: Pair<Result, Long>? = null

    @Synchronized fun begin(expectedQuery: String?): Request {
        require(expectedQuery == null || expectedQuery.isNotBlank() && expectedQuery.length <= 300)
        result = null
        return Request(++serial, expectedQuery, elapsed() + REQUEST_MS).also { request = it }
    }

    @Synchronized fun active(): Request? = request?.takeIf { elapsed() < it.expires }

    @Synchronized fun pending(): Request? = request

    @Synchronized fun current(id: Long): Request? = request?.takeIf { it.id == id }

    @Synchronized fun complete(id: Long, reading: BrowserReading?): Boolean {
        val pending = request?.takeIf { it.id == id } ?: return false
        if (reading != null && elapsed() >= pending.expires) { clear(); return false }
        request = null
        result = Result(reading) to (elapsed() + RESULT_MS)
        return true
    }

    @Synchronized fun consume(): Result? {
        val answer = result?.takeIf { elapsed() < it.second }?.first
        clear()
        return answer
    }

    @Synchronized fun clear() { request = null; result = null }

    companion object {
        const val REQUEST_MS = 25_000L
        const val RESULT_MS = 60_000L
    }
}
