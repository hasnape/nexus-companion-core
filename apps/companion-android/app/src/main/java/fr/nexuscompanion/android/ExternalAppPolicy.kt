package fr.nexuscompanion.android

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri

/** User commands only. Notes and articles never pass through this command parser. */
internal object ExternalAppPolicy {
    enum class App(val label: String, val packageName: String) {
        MAPS("Google Maps", "com.google.android.apps.maps"), CHROME("Chrome", "com.android.chrome")
    }
    enum class Operation { HOME, SEARCH, DIRECTIONS, URL }
    enum class Place { HOME, WORK }
    data class Request(val app: App, val operation: Operation, val target: String = "") {
        init {
            require((app == App.MAPS && operation != Operation.URL) || (app == App.CHROME && operation != Operation.DIRECTIONS))
            require(if (operation == Operation.HOME) target.isEmpty() else validTarget(target, if (operation == Operation.URL) 2048 else 300))
            if (operation == Operation.URL) require(safeWebUrl(target))
            require(uri().toString().length <= 2048)
        }
        fun uri(): Uri = when {
            operation == Operation.URL -> Uri.parse(target)
            app == App.CHROME && operation == Operation.HOME -> Uri.parse("https://www.google.com/")
            app == App.CHROME -> Uri.Builder().scheme("https").authority("www.google.com").path("/search").appendQueryParameter("q", target).build()
            operation == Operation.HOME -> Uri.parse("https://www.google.com/maps/@?api=1&map_action=map")
            operation == Operation.DIRECTIONS -> Uri.Builder().scheme("https").authority("www.google.com").path("/maps/dir/")
                .appendQueryParameter("api", "1").appendQueryParameter("destination", target).build()
            else -> Uri.Builder().scheme("https").authority("www.google.com").path("/maps/search/")
                .appendQueryParameter("api", "1").appendQueryParameter("query", target).build()
        }
        fun openingText(): String = when (operation) {
            Operation.HOME -> "J’ouvre ${app.label}."
            Operation.SEARCH -> "J’ouvre ${app.label} pour chercher « $target »."
            Operation.DIRECTIONS -> "J’ouvre Google Maps pour préparer l’itinéraire vers « $target »."
            Operation.URL -> "J’ouvre cette page dans Chrome : $target"
        }
    }
    sealed interface Command {
        data class Open(val request: Request) : Command
        data class SavedPlace(val place: Place, val operation: Operation) : Command
        data class Clarify(val text: String) : Command
    }

    private val secrets = Regex("\\b(?:mot de passe|password|iban|carte bancaire|code pin|code de validation|access.token|api.key)\\b", RegexOption.IGNORE_CASE)
    private fun validTarget(value: String, max: Int): Boolean = value.isNotBlank() && value.length <= max &&
        value.none { it.isISOControl() } && !secrets.containsMatchIn(VoiceTranscriptPolicy.commandWords(value))
    private fun safeWebUrl(value: String): Boolean {
        val uri = Uri.parse(value)
        val host = uri.host ?: return false
        return uri.scheme == "https" && uri.userInfo == null && (uri.port == -1 || uri.port == 443) &&
            host.matches(Regex("(?=.{1,253}$)[a-zA-Z0-9](?:[a-zA-Z0-9.-]*[a-zA-Z0-9])?\\.[a-zA-Z]{2,63}")) &&
            !Regex("[\\s\\\\]").containsMatchIn(value) && !Regex("(?:[?&])(?:access_token|id_token|password|api_key)=", RegexOption.IGNORE_CASE).containsMatchIn(value)
    }

    fun parse(message: String): Command? {
        val text = VoiceTranscriptPolicy.parse(message).command.trim().replaceFirst(
            Regex("^(?:peux[- ]tu|tu peux|pourrais[- ]tu)\\s+", RegexOption.IGNORE_CASE), "")
            .replaceFirst(Regex("^ouvrir\\s+", RegexOption.IGNORE_CASE), "ouvre ")
            .replaceFirst(Regex("^chercher\\s+", RegexOption.IGNORE_CASE), "cherche ")
            .replaceFirst(Regex("^rechercher\\s+", RegexOption.IGNORE_CASE), "recherche ")
        val words = VoiceTranscriptPolicy.commandWords(text)
        if (words in setOf("ouvre chrome", "ouvre google chrome", "ouvre google")) return Command.Open(Request(App.CHROME, Operation.HOME))
        if (words in setOf("ouvre maps", "ouvre google maps", "affiche maps", "affiche google maps")) return Command.Open(Request(App.MAPS, Operation.HOME))
        val maps = Regex("^(?:ouvre|affiche|cherche dans|cherche sur|recherche dans|recherche sur) (?:google maps|maps)(?:\\s+(?:(?:à|a|sur|pour)\\s+)?(.+))?$", RegexOption.IGNORE_CASE).matchEntire(text)
        if (maps != null) return mapTarget(maps.groupValues[1].trim().trimEnd('?', '!').trim(), Operation.SEARCH)
        val route = Regex("^(?:(?:prépare|prepare|affiche|calcule)(?:[- ]moi)? (?:un |l['’])?)?itin[ée]raire (?:vers|pour) (.+)$", RegexOption.IGNORE_CASE).matchEntire(text)
        if (route != null) return mapTarget(route.groupValues[1].trim().trimEnd('?', '!').trim(), Operation.DIRECTIONS)
        val web = Regex("^(?:cherche|recherche) (?:sur google|dans (?:google )?chrome|sur (?:google )?chrome)(?:\\s+(.+))?$", RegexOption.IGNORE_CASE).matchEntire(text)
        if (web != null) return request(App.CHROME, Operation.SEARCH, web.groupValues[1].trim().trimEnd('?', '!').trim())
        val page = Regex("^ouvre (?:(?:dans )?(?:google )?chrome (.+)|(.+) dans (?:google )?chrome)$", RegexOption.IGNORE_CASE).matchEntire(text)
        if (page != null) return request(App.CHROME, Operation.URL, page.groupValues[1].ifEmpty { page.groupValues[2] }.trim())
        return null
    }

    private fun mapTarget(target: String, operation: Operation): Command {
        val place = when (VoiceTranscriptPolicy.commandWords(target)) {
            "chez moi", "mon domicile", "ma maison", "la maison", "mon adresse" -> Place.HOME
            "mon travail", "au travail", "mon lieu de travail", "mon adresse professionnelle" -> Place.WORK
            else -> null
        }
        return if (place != null) Command.SavedPlace(place, operation) else request(App.MAPS, operation, target)
    }

    private fun request(app: App, operation: Operation, target: String): Command = try {
        Command.Open(Request(app, operation, target))
    } catch (_: IllegalArgumentException) {
        Command.Clarify(if (operation == Operation.URL) "Donne-moi une adresse de page HTTPS valide, sans identifiants ni code secret."
            else "Précise un lieu ou un sujet court, sans code secret, à ouvrir dans ${app.label}.")
    }

    /** Prefer the named app, then let Android choose a compatible app. No package inventory. */
    fun launch(activity: Activity, request: Request): Boolean {
        fun start(preferred: Boolean): Boolean = try {
            activity.startActivity(Intent(Intent.ACTION_VIEW, request.uri()).apply {
                addCategory(Intent.CATEGORY_BROWSABLE)
                if (preferred) setPackage(request.app.packageName)
            }); true
        } catch (_: ActivityNotFoundException) { false } catch (_: SecurityException) { false }
        return start(true) || start(false)
    }
}
