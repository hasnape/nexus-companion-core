package fr.nexuscompanion.android

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import java.net.URL
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import javax.net.ssl.HttpsURLConnection

/** Model installation is always a deliberate user action, independent of online conversation. */
class LocalModelActivity : Activity() {
    private val worker = Executors.newSingleThreadExecutor()
    private val cancelled = AtomicBoolean()
    private var busy = false
    private var closed = false
    private lateinit var status: TextView
    private lateinit var toggle: Switch
    private lateinit var store: LocalModelStore
    private val actions = mutableListOf<Button>()
    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        title = "Modèle de langage local"
        store = LocalModelStore(this)
        val panel = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(28, 28, 28, 28) }
        setContentView(ScrollView(this).apply { addView(panel) })
        fun text(value: String) = TextView(this).also { it.text = value; it.textSize = 17f; panel.addView(it) }
        text("Répondre hors ligne avec LFM2.5")
        text("Le modèle s’installe séparément : environ 731 Mo. Il répond aux questions générales ; les notes personnelles et les commandes restent gérées par le moteur Nexus. Ses réponses peuvent contenir des erreurs et ne sont jamais enregistrées automatiquement.")
        status = text("")
        toggle = Switch(this).apply {
            text = getString(R.string.llm_ui_1)
            isChecked = store.enabled
            setOnCheckedChangeListener { _, checked -> store.enabled = checked; refresh() }
        }.also(panel::addView)
        fun button(label: String, action: () -> Unit) = Button(this).also { b ->
            b.text = label; b.setOnClickListener { action() }; panel.addView(b); actions.add(b)
        }
        button("Télécharger le modèle officiel · 731 Mo") {
            transfer {
                val connection = URL(LocalModelSpec.URL).openConnection() as HttpsURLConnection
                connection.connectTimeout = 20_000; connection.readTimeout = 30_000
                try { store.import(connection.inputStream) { cancelled.get() } } finally { connection.disconnect() }
            }
        }
        button("Importer le fichier GGUF officiel") {
            startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*"), 451)
        }
        button("Supprimer le modèle local") { transfer { store.delete() } }
        Button(this).apply {
            text = getString(R.string.llm_ui_2); setOnClickListener { cancelled.set(true) }; panel.addView(this)
        }
        text("La vérification SHA-256 doit réussir avant l’activation. Le calcul reste sur le téléphone. Il se met en retrait en cas de manque de RAM, de batterie faible, de chauffe ou d’économie d’énergie.")
        text("Calcul disponible sous Android 64 bits avec au moins 1,5 Gio de RAM libre. Si la batterie est à 15 % ou moins, branche le téléphone. Le téléchargement est plus confortable en Wi-Fi.")
        text("Modèle : ${LocalModelSpec.LABEL}\nLicence : Liquid AI LFM Open License 1.0 ; texte complet dans les notices de l’application.")
        refresh()
    }
    private fun refresh(message: String? = null) {
        val installed = store.installed()
        status.text = message ?: if (busy) "Opération en cours…" else if (installed) "Modèle vérifié et installé." else "Modèle non installé. Le moteur Nexus actuel reste disponible."
        toggle.isEnabled = !busy && installed
        toggle.isChecked = store.enabled
        actions.forEach { it.isEnabled = !busy }
    }
    private fun transfer(action: () -> Unit) {
        if (busy || closed) return
        busy = true; cancelled.set(false); refresh()
        worker.execute {
            val message = try { action(); "Opération terminée." } catch (failure: Exception) {
                if (cancelled.get()) "Opération annulée." else failure.message?.take(180) ?: "Impossible d’installer le modèle."
            }
            runOnUiThread { if (!closed) { busy = false; refresh(message) } }
        }
    }
    @Deprecated("Android activity result")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        val uri = data?.data ?: return
        if (requestCode == 451 && resultCode == RESULT_OK) transfer {
            store.import(contentResolver.openInputStream(uri) ?: error("Fichier inaccessible.")) { cancelled.get() }
        }
    }
    override fun onDestroy() { closed = true; cancelled.set(true); worker.shutdownNow(); super.onDestroy() }
}
