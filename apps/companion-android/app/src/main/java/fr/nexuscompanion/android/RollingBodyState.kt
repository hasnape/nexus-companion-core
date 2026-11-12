package fr.nexuscompanion.android

import org.json.JSONObject
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/** Replays the shared core's trajectory. No motor transport or physical actuation. Main thread only. */
internal class RollingBodyState {
    data class Pose(val xM: Double = 0.0, val yM: Double = 0.0, val headingRad: Double = 0.0, val travelledM: Double = 0.0)
    data class Frame(val pose: Pose, val moving: Boolean = false, val steeringRad: Double = 0.0,
        val leftWheelRad: Double = 0.0, val rightWheelRad: Double = 0.0, val steeringAtFront: Boolean = true)
    data class Sample(val elapsedMs: Long, val xM: Double, val yM: Double, val headingRad: Double, val distanceM: Double)
    data class Plan(val durationMs: Long, val steeringRad: Double, val leftRadps: Double, val rightRadps: Double,
        val steeringAtFront: Boolean, val samples: List<Sample>) {
        companion object {
            fun read(json: JSONObject): Plan {
                require(json.getInt("protocolVersion") == 1 && json.getString("execution") == "simulation")
                val duration = json.getJSONObject("request").getLong("durationMs")
                val angle = json.getDouble("steeringRad")
                val left = json.getDouble("leftWheelRadps"); val right = json.getDouble("rightWheelRadps")
                val axle = json.getString("steeringAxle")
                require(duration in 1..5000 && angle.isFinite() && abs(angle) <= 0.7 &&
                    listOf(left, right).all { it.isFinite() && abs(it) <= 12 } && axle in listOf("front", "rear"))
                val data = json.getJSONArray("samples")
                require(data.length() in 2..52)
                val samples = (0 until data.length()).map { i ->
                    val sample = data.getJSONObject(i)
                    Sample(sample.getLong("elapsedMs"), sample.getDouble("xM"), sample.getDouble("yM"),
                        sample.getDouble("headingRad"), sample.getDouble("distanceM")).also {
                        require(it.elapsedMs in 0..duration && listOf(it.xM, it.yM, it.headingRad, it.distanceM).all { value -> value.isFinite() && abs(value) <= 5 })
                    }
                }
                require(samples.first().elapsedMs == 0L && samples.last().elapsedMs == duration &&
                    samples.zipWithNext().all { (a, b) -> b.elapsedMs > a.elapsedMs })
                return Plan(duration, angle, left, right, axle == "front", samples)
            }
        }
    }
    private var origin = Pose()
    private var idle = Frame(origin)
    private var plan: Plan? = null
    private var startedMs = 0L

    fun start(value: Plan, nowMs: Long) { stop(nowMs); plan = value; startedMs = nowMs }
    fun stop(nowMs: Long): Frame {
        val current = frame(nowMs)
        origin = current.pose; plan = null
        idle = current.copy(moving = false, steeringRad = 0.0)
        return idle
    }
    fun restore(pose: Pose) {
        require(listOf(pose.xM, pose.yM, pose.headingRad, pose.travelledM).all { it.isFinite() })
        origin = pose; plan = null; idle = Frame(pose)
    }
    fun frame(nowMs: Long): Frame {
        val motion = plan ?: return idle
        val elapsed = (nowMs - startedMs).coerceIn(0, motion.durationMs)
        val after = motion.samples.first { it.elapsedMs >= elapsed }
        val before = motion.samples.last { it.elapsedMs <= elapsed }
        val ratio = if (after.elapsedMs == before.elapsedMs) 0.0 else (elapsed - before.elapsedMs).toDouble() / (after.elapsedMs - before.elapsedMs)
        fun mix(a: Double, b: Double) = a + (b - a) * ratio
        val x = mix(before.xM, after.xM); val y = mix(before.yM, after.yM)
        val pose = Pose(origin.xM + cos(origin.headingRad) * x - sin(origin.headingRad) * y,
            origin.yM + sin(origin.headingRad) * x + cos(origin.headingRad) * y,
            origin.headingRad + mix(before.headingRad, after.headingRad), origin.travelledM + abs(mix(before.distanceM, after.distanceM)))
        val frame = Frame(pose, elapsed < motion.durationMs, motion.steeringRad,
            motion.leftRadps * elapsed / 1000, motion.rightRadps * elapsed / 1000, motion.steeringAtFront)
        if (!frame.moving) { origin = pose; plan = null; idle = frame.copy(steeringRad = 0.0) }
        return frame
    }
}
