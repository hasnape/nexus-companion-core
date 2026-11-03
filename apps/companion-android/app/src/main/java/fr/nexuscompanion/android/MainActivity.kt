package fr.nexuscompanion.android

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Bundle
import android.graphics.Color
import android.graphics.Typeface
import android.view.Gravity
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.ScrollView

/** Native first-run shell: no WebView, browser, server, or network permission. */
class MainActivity : Activity() {
    private lateinit var controller: NativeVoiceController
    private lateinit var library: LocalKnowledgeLibrary
    private lateinit var face: TextView
    private lateinit var status: TextView
    private lateinit var answer: TextView
    private lateinit var voiceButton: Button
    private var voiceActive = false
    private var autoListenEnabled = true
    private var foreground = false
    private val voicePreferences by lazy { getSharedPreferences("nexus_voice_preferences", MODE_PRIVATE) }
    private val audioRequestCode = 410

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        library = LocalKnowledgeLibrary(applicationContext)
        autoListenEnabled = voicePreferences.getBoolean("auto_listen_enabled", true)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(28, 48, 28, 28)
            setBackgroundColor(Color.rgb(10, 22, 43))
        }
        fun label(text: String, size: Float): TextView = TextView(this).apply {
            this.text = text
            textSize = size
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
        }
        face = label("●   ●\n⌣", 68f).apply {
            setTextColor(Color.CYAN)
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, 40, 0, 40)
        }
        status = label("Nexus Companion — Android natif, hors ligne", 17f)
        answer = label("Bonjour ! Je suis Nexus.", 19f)
        val input = EditText(this).apply {
            hint = "Écris à Nexus"
            setSingleLine(true)
            setTextColor(Color.WHITE)
            setHintTextColor(Color.LTGRAY)
        }
        val send = Button(this).apply { text = "Envoyer" }
        val remember = Button(this).apply { text = "Ajouter à ma bibliothèque locale" }
        val searchLibrary = Button(this).apply { text = "Chercher dans ma bibliothèque" }
        voiceButton = Button(this).apply { text = "Activer le micro local" }
        val layout = LinearLayout.LayoutParams(-1, -2)
        root.addView(face, layout)
        root.addView(status, layout)
        root.addView(answer, layout)
        root.addView(input, layout)
        root.addView(send, layout)
        root.addView(remember, layout)
        root.addView(searchLibrary, layout)
        root.addView(voiceButton, layout)
        setContentView(ScrollView(this).apply { addView(root) })

        controller = NativeVoiceController(
            context = this,
            onStatus = { status.text = it },
            onCommand = { message -> respond(message) },
            onSpeechState = { audible ->
                face.text = if (audible) "●   ●\n◯" else "●   ●\n⌣"
            },
            onSessionState = { active ->
                voiceActive = active
                voiceButton.text = if (active) "Désactiver le micro" else "Activer le micro local"
            }
        )
        remember.setOnClickListener {
            val note = input.text.toString().trim()
            if (note.isNotEmpty()) {
                try {
                    library.addUserNote(note.take(80), note)
                    answer.text = "Note enregistrée localement (non vérifiée)."
                    input.setText("")
                } catch (_: IllegalArgumentException) {
                    answer.text = "Note trop longue ou invalide (2000 caractères maximum)."
                } catch (_: IllegalStateException) {
                    answer.text = "Bibliothèque locale pleine."
                } catch (_: Exception) {
                    answer.text = "Impossible de lire ou enregistrer la bibliothèque. Données conservées."
                }
            }
        }
        searchLibrary.setOnClickListener {
            val query = input.text.toString().trim()
            try {
                val matches = library.search(query)
                answer.text = if (matches.isEmpty()) "Aucune note locale correspondante."
                    else matches.joinToString("\n\n") { "${it.title} : ${it.excerpt}\nSource : ${it.source} (non vérifiée)" }
            } catch (_: Exception) {
                answer.text = "Bibliothèque inaccessible : aucune donnée supprimée."
            }
        }
        send.setOnClickListener {
            val message = input.text.toString().trim()
            if (message.isNotEmpty()) {
                respond(message)
                input.setText("")
            }
        }
        voiceButton.setOnClickListener {
            if (voiceActive) {
                autoListenEnabled = false
                voicePreferences.edit().putBoolean("auto_listen_enabled", false).apply()
                controller.stop()
                voiceActive = false
                voiceButton.text = "Activer le micro local"
            } else if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                autoListenEnabled = true
                voicePreferences.edit().putBoolean("auto_listen_enabled", true).apply()
                activateVoice()
            } else {
                requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), audioRequestCode)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        foreground = true
        if (!::controller.isInitialized || !autoListenEnabled || voiceActive) return
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            activateVoice()
        } else if (!voicePreferences.getBoolean("microphone_permission_prompted", false)) {
            voicePreferences.edit().putBoolean("microphone_permission_prompted", true).apply()
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), audioRequestCode)
        } else {
            status.text = "Micro désactivé : autorise-le dans Android puis active-le ici."
        }
    }

    private fun respond(message: String) {
        val reply = OfflineConversation.reply(message)
        answer.text = reply
        if (voiceActive) controller.speak(reply)
    }

    private fun activateVoice() {
        if (!controller.available()) {
            status.text = "Ce téléphone n'a pas de reconnaissance Android hors ligne. Texte disponible."
            return
        }
        controller.start()
        // The controller reports its actual session state; do not assume startup succeeded.
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == audioRequestCode) {
            if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED && foreground) {
                autoListenEnabled = true
                voicePreferences.edit().putBoolean("auto_listen_enabled", true).apply()
                activateVoice()
            }
            else status.text = "Micro refusé : le mode texte reste disponible."
        }
    }

    override fun onStop() {
        super.onStop()
        foreground = false
        // No background surveillance: the microphone is off outside the foreground.
        if (::controller.isInitialized) controller.stop()
        voiceActive = false
        if (::voiceButton.isInitialized) voiceButton.text = "Activer le micro local"
    }

    override fun onDestroy() {
        if (::controller.isInitialized) controller.stop()
        super.onDestroy()
    }
}
