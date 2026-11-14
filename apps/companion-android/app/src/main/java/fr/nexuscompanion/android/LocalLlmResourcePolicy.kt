package fr.nexuscompanion.android

/**
 * Advisory on-device inference budget. The future native LLM adapter must also
 * enforce its own measured memory, time and cancellation limits.
 * No model is downloaded, activated or loaded by this policy.
 */
internal object LocalLlmResourcePolicy {
    private const val MIB = 1024L * 1024L
    private const val GIB = 1024L * MIB

    enum class Mode { ECO, ADAPTIVE, MAXIMUM }
    enum class Tier { RULES_ONLY, LIGHT, COMPACT, STANDARD, ADVANCED }

    data class Plan(
        val tier: Tier,
        val modelHint: String?,
        val cpuThreads: Int,
        val contextTokens: Int,
        val outputTokens: Int,
        val limits: LocalLlmMemoryContext.Limits,
        val reason: String
    ) {
        val inferenceAllowed: Boolean get() = tier != Tier.RULES_ONLY
    }

    /**
     * Available system RAM is not free RAM reserved to this app. Always leave
     * a buffer for Android, voice recognition, TTS and other processes.
     * Thermal status follows Android's 0..6 scale (3 = severe).
     */
    fun select(
        snapshot: DeviceResources.Snapshot,
        mode: Mode = Mode.ADAPTIVE,
        foreground: Boolean = true
    ): Plan {
        fun disabled(reason: String) = Plan(
            Tier.RULES_ONLY, null, 0, 0, 0,
            LocalLlmMemoryContext.Limits(), reason
        )
        if (!foreground) return disabled("background")
        if (snapshot.lowMemory) return disabled("android_memory_pressure")
        if (snapshot.thermalStatus >= 3) return disabled("thermal_severe")
        if (snapshot.batteryPercent != null && snapshot.batteryPercent <= 10 && !snapshot.charging)
            return disabled("battery_critical")
        if (snapshot.freeStorage < 512L * MIB) return disabled("low_storage")
        if (snapshot.availableRam < 1280L * MIB) return disabled("insufficient_ram")

        // Thresholds are conservative admission estimates, not measured peak
        // RSS guarantees; the inference adapter must still fail closed.
        var tier = when {
            snapshot.totalRam >= 12 * GIB && snapshot.availableRam >= 8 * GIB &&
                snapshot.processors >= 8 -> Tier.ADVANCED
            snapshot.totalRam >= 8 * GIB && snapshot.availableRam >= 4608 * MIB &&
                snapshot.processors >= 6 -> Tier.STANDARD
            snapshot.totalRam >= 6 * GIB && snapshot.availableRam >= 2560 * MIB &&
                snapshot.processors >= 4 -> Tier.COMPACT
            else -> Tier.LIGHT
        }
        val constrained = snapshot.powerSave ||
            (snapshot.batteryPercent != null && snapshot.batteryPercent <= 20 && !snapshot.charging) ||
            snapshot.thermalStatus >= 2
        if (mode == Mode.ECO || constrained) tier = downgrade(tier)
        // Maximum uses more of the *admitted* budget, never bypasses guards.
        val threads = (snapshot.processors - 2).coerceIn(1, if (mode == Mode.MAXIMUM && !constrained) 8 else 6)
        val maxThreads = if (constrained || mode == Mode.ECO) minOf(threads, 2) else threads
        return when (tier) {
            Tier.RULES_ONLY -> disabled("constrained")
            Tier.LIGHT -> Plan(tier, "0.5B Q4", maxThreads, 1024,
                if (mode == Mode.ECO) 64 else 96,
                LocalLlmMemoryContext.Limits(maxNotes = 3, maxExcerptChars = 500, maxContextChars = 2100),
                if (constrained) "resources_constrained" else "ready")
            Tier.COMPACT -> Plan(tier, "1.5B Q4", maxThreads, 2048,
                if (mode == Mode.MAXIMUM && !constrained) 192 else 128,
                LocalLlmMemoryContext.Limits(maxNotes = 4, maxExcerptChars = 640, maxContextChars = 3500), "ready")
            Tier.STANDARD -> Plan(tier, "3B Q4", maxThreads, 3072,
                if (mode == Mode.MAXIMUM && !constrained) 256 else 160,
                LocalLlmMemoryContext.Limits(maxNotes = 6, maxExcerptChars = 800, maxContextChars = 5500), "ready")
            Tier.ADVANCED -> Plan(tier, "7B Q4", maxThreads, 4096,
                if (mode == Mode.MAXIMUM && !constrained) 320 else 192,
                LocalLlmMemoryContext.Limits(maxNotes = 8, maxExcerptChars = 1000, maxContextChars = 9000), "ready")
        }
    }

    private fun downgrade(tier: Tier): Tier = when (tier) {
        Tier.ADVANCED -> Tier.STANDARD
        Tier.STANDARD -> Tier.COMPACT
        Tier.COMPACT -> Tier.LIGHT
        Tier.LIGHT, Tier.RULES_ONLY -> Tier.LIGHT
    }
}
