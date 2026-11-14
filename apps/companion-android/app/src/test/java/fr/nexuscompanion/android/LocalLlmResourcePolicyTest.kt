package fr.nexuscompanion.android

import org.junit.Assert.*
import org.junit.Test

class LocalLlmResourcePolicyTest {
    private val gib = 1024L * 1024 * 1024
    private fun phone(total: Int, available: Double, cores: Int = 8,
                      thermal: Int = 0, lowMemory: Boolean = false,
                      battery: Int = 75, charging: Boolean = false,
                      powerSave: Boolean = false, storage: Long = 12 * gib) =
        DeviceResources.Snapshot(
            processors = cores,
            totalRam = total * gib,
            availableRam = (available * gib).toLong(),
            heapBytes = 512L * 1024 * 1024,
            freeStorage = storage,
            lowMemory = lowMemory,
            powerSave = powerSave,
            thermalStatus = thermal,
            batteryPercent = battery,
            charging = charging
        )

    @Test fun fourGigabytePhoneAdaptsToRealAvailableMemory() {
        val stressed = LocalLlmResourcePolicy.select(phone(4, 1.0))
        assertEquals(LocalLlmResourcePolicy.Tier.RULES_ONLY, stressed.tier)
        val recovered = LocalLlmResourcePolicy.select(phone(4, 1.6))
        assertEquals(LocalLlmResourcePolicy.Tier.LIGHT, recovered.tier)
        assertEquals("0.5B Q4", recovered.modelHint)
    }

    @Test fun futureDeviceCanUseMoreContextWithoutMigratingMemory() {
        val current = LocalLlmResourcePolicy.select(phone(4, 1.7))
        val future = LocalLlmResourcePolicy.select(phone(12, 9.0))
        assertEquals(LocalLlmResourcePolicy.Tier.ADVANCED, future.tier)
        assertTrue(future.contextTokens > current.contextTokens)
        assertTrue(future.limits.maxNotes > current.limits.maxNotes)
        assertTrue(future.limits.maxContextChars > current.limits.maxContextChars)
        val memories = (1..8).map { LocalLlmMemoryContext.Evidence("n$it", "Sujet $it", "Un souvenir concret $it") }
        val prompt = LocalLlmMemoryContext.prepare("Que sais-tu ?", memories, false, "Alif", future.limits)
        assertEquals(8, prompt.evidenceIds.size)
        assertTrue(prompt.system.contains("Tu es Alif"))
    }

    @Test fun middleTierAndMaximumAreNotAllowedToBypassSafety() {
        val mid = LocalLlmResourcePolicy.select(phone(8, 5.0))
        assertEquals(LocalLlmResourcePolicy.Tier.STANDARD, mid.tier)
        val maximum = LocalLlmResourcePolicy.select(phone(12, 9.0),
            LocalLlmResourcePolicy.Mode.MAXIMUM)
        assertEquals(LocalLlmResourcePolicy.Tier.ADVANCED, maximum.tier)
        assertTrue(maximum.cpuThreads <= 8)
        assertFalse(LocalLlmResourcePolicy.select(phone(12, 9.0, thermal = 3),
            LocalLlmResourcePolicy.Mode.MAXIMUM).inferenceAllowed)
        assertFalse(LocalLlmResourcePolicy.select(phone(12, 9.0, lowMemory = true),
            LocalLlmResourcePolicy.Mode.MAXIMUM).inferenceAllowed)
        assertFalse(LocalLlmResourcePolicy.select(phone(12, 9.0),
            LocalLlmResourcePolicy.Mode.MAXIMUM, foreground = false).inferenceAllowed)
    }

    @Test fun batteryAndPowerSavingReduceTheTier() {
        assertFalse(LocalLlmResourcePolicy.select(phone(8, 5.0, battery = 8)).inferenceAllowed)
        assertTrue(LocalLlmResourcePolicy.select(phone(8, 5.0, battery = 8, charging = true)).inferenceAllowed)
        val powerSave = LocalLlmResourcePolicy.select(phone(8, 5.0, powerSave = true))
        assertEquals(LocalLlmResourcePolicy.Tier.COMPACT, powerSave.tier)
        assertTrue(powerSave.cpuThreads <= 2)
        assertFalse(LocalLlmResourcePolicy.select(phone(8, 5.0, storage = 200L * 1024 * 1024)).inferenceAllowed)
    }

    @Test fun boundedMemoryPromptsRejectUnreasonableProfiles() {
        try {
            LocalLlmMemoryContext.Limits(maxNotes = 100)
            fail("Should reject unbounded note count")
        } catch (_: IllegalArgumentException) {
            // intentionally rejected
        }
    }
}
