package fr.nexuscompanion.android

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import android.os.StatFs

/** Local telemetry, no hardware identifier. Heap allowance matters as well as physical RAM. */
internal object DeviceResources {
    data class Snapshot(val processors: Int, val totalRam: Long, val availableRam: Long,
        val heapBytes: Long, val freeStorage: Long, val lowMemory: Boolean,
        val powerSave: Boolean, val thermalStatus: Int, val batteryPercent: Int?, val charging: Boolean,
        val abis: List<String> = emptyList())
    data class Budget(val label: String, val contextNotes: Int, val requestChars: Int,
        val learningBatch: Int, val brainMs: Long, val instructions: Long, val learningAllowed: Boolean)
    private const val MIB = 1024L * 1024

    fun inspect(context: Context): Snapshot {
        val manager = context.getSystemService(ActivityManager::class.java)
        val info = ActivityManager.MemoryInfo().also { manager.getMemoryInfo(it) }
        val power = context.getSystemService(PowerManager::class.java)
        val battery = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = battery?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = battery?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val status = battery?.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        return Snapshot(Runtime.getRuntime().availableProcessors().coerceAtLeast(1), info.totalMem,
            info.availMem, minOf(Runtime.getRuntime().maxMemory(), manager.memoryClass.toLong() * MIB),
            StatFs(context.filesDir.absolutePath).availableBytes, info.lowMemory || manager.isLowRamDevice,
            power.isPowerSaveMode, power.currentThermalStatus,
            if (level >= 0 && scale > 0) (level.toLong() * 100 / scale).toInt().coerceIn(0, 100) else null,
            status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL,
            Build.SUPPORTED_ABIS.toList())
    }

    fun budget(snapshot: Snapshot): Budget {
        val pressure = snapshot.lowMemory || snapshot.availableRam < 512 * MIB || snapshot.heapBytes < 192 * MIB
        val hot = snapshot.thermalStatus >= PowerManager.THERMAL_STATUS_MODERATE
        val lowBattery = snapshot.batteryPercent?.let { it <= 15 && !snapshot.charging } ?: false
        val limited = pressure || hot || lowBattery || snapshot.powerSave || snapshot.freeStorage < 256 * MIB
        val extended = !limited && snapshot.processors >= 6 && snapshot.totalRam >= 6 * 1024 * MIB
            && snapshot.availableRam >= 2 * 1024 * MIB && snapshot.heapBytes >= 384 * MIB
        return when {
            limited -> Budget("Économe", 12, 96_000, 4, 4_000, 240_000_000, false)
            extended -> Budget("Étendu", NativeCapacity.MAX_CONTEXT_NOTES, NativeCapacity.MAX_REQUEST_CHARS,
                32, NativeCapacity.BRAIN_TIME_MS.toLong(), NativeCapacity.BRAIN_INSTRUCTIONS.toLong(), true)
            else -> Budget("Équilibré", 48, 256_000, 12, 5_000, 480_000_000, true)
        }
    }

    fun describe(snapshot: Snapshot, budget: Budget): String {
        fun gib(bytes: Long) = String.format(java.util.Locale.FRANCE, "%.1f", bytes.toDouble() / (1024 * MIB))
        return "Profil ${budget.label} • ${snapshot.processors} processeurs logiques\n" +
            "RAM : ${gib(snapshot.totalRam)} Go ; disponible : ${gib(snapshot.availableRam)} Go\n" +
            "Limite du tas Java : ${snapshot.heapBytes / MIB} Mo (mémoire native du LLM distincte)\n" +
            "Stockage disponible : ${gib(snapshot.freeStorage)} Go\n" +
            "Batterie : ${snapshot.batteryPercent?.let { "$it %" } ?: "inconnue"} ; ${if (snapshot.charging) "en charge" else "sur batterie"}\n" +
            "Chauffe Android : ${snapshot.thermalStatus} ; économie d’énergie : ${if (snapshot.powerSave) "oui" else "non"}\n" +
            "Contexte : jusqu’à ${budget.contextNotes} notes ; consolidation : ${if (budget.learningAllowed) "${budget.learningBatch} notes par lot" else "en pause pour préserver les ressources"}.\n\n" +
            "Nexus ajuste ces budgets à chaque échange et lot. Le moteur local utilise le processeur ; aucun entraînement GPU ou NPU n’est annoncé."
    }
}
