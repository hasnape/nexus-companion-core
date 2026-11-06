package fr.nexuscompanion.android

import org.mozilla.javascript.Context
import org.mozilla.javascript.ContextFactory
import org.mozilla.javascript.Function
import org.mozilla.javascript.Scriptable
import org.mozilla.javascript.ScriptableObject

/** Only the packaged, trusted bundle is evaluated. Requests are function arguments, never code. */
internal class SharedBrainRuntime(
    private val source: String,
    private val instructionLimit: Long = 3_000_000L,
    private val timeLimitMs: Long = 2_500L
) : AutoCloseable {
    companion object { const val MAX_REQUEST_CHARS = 40_000 }
    private var scope: Scriptable? = null
    private var closed = false
    private var instructions = 0L
    private var deadline = 0L
    private val factory = object : ContextFactory() {
        override fun makeContext(): Context = super.makeContext().apply {
            languageVersion = Context.VERSION_ES6
            setInterpretedMode(true) // Android has no JVM bytecode class loader.
            setClassShutter { false }
            instructionObserverThreshold = 10_000
        }
        override fun observeInstructionCount(context: Context, count: Int) {
            instructions += count
            check(!Thread.currentThread().isInterrupted && instructions <= instructionLimit && System.nanoTime() < deadline) {
                "Local brain execution limit reached"
            }
        }
    }

    init { require(source.length in 1..500_000) }

    @Synchronized
    fun replyJson(request: String): String {
        check(!closed) { "Local brain closed" }
        require(request.length <= MAX_REQUEST_CHARS) { "Local brain request too large" }
        instructions = 0
        deadline = System.nanoTime() + timeLimitMs * 1_000_000L
        return try {
            factory.call { context ->
                val global = scope ?: context.initSafeStandardObjects().also {
                    context.evaluateString(it, source, "nexus-brain.js", 1, null)
                    scope = it
                }
                val bridge = ScriptableObject.getProperty(global, "NexusNativeBrain") as? Scriptable
                    ?: error("Packaged brain entry point missing")
                val function = ScriptableObject.getProperty(bridge, "handleNativeTurnJson") as? Function
                    ?: error("Packaged brain function missing")
                val result = function.call(context, global, bridge, arrayOf(request))
                check(result is CharSequence && result.length <= 16_000) { "Invalid local brain response" }
                result.toString()
            }
        } catch (failure: RuntimeException) {
            // A failed evaluation cannot contaminate the next request's global state.
            scope = null
            throw failure
        }
    }

    @Synchronized
    override fun close() {
        closed = true
        scope = null
    }
}
