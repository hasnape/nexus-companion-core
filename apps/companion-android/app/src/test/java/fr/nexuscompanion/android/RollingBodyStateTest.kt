package fr.nexuscompanion.android

import org.junit.Assert.*
import org.junit.Test

class RollingBodyStateTest {
    private fun plan() = RollingBodyState.Plan(1000, 0.0, 2.0, 2.0, true, listOf(
        RollingBodyState.Sample(0, 0.0, 0.0, 0.0, 0.0), RollingBodyState.Sample(1000, 0.1, 0.0, 0.0, 0.1)))
    @Test fun interpolatesAndStopsAtTheDeadlineEvenWhenAnUpdateIsLate() {
        val body = RollingBodyState(); body.start(plan(), 100)
        assertEquals(0.05, body.frame(600).pose.xM, 0.0001)
        val end = body.frame(5000)
        assertFalse(end.moving); assertEquals(0.1, end.pose.xM, 0.0001)
        assertEquals(0.1, body.frame(9000).pose.xM, 0.0001)
    }
    @Test fun stopFreezesThePoseAndTheNextCommandContinuesFromThere() {
        val body = RollingBodyState(); body.start(plan(), 0)
        body.stop(400)
        assertEquals(0.04, body.frame(1000).pose.xM, 0.0001)
        body.start(plan(), 2000)
        assertEquals(0.14, body.frame(3000).pose.xM, 0.0001)
        assertEquals(0.14, body.frame(3000).pose.travelledM, 0.0001)
    }
    @Test fun restoredPoseDoesNotResumeAMovement() {
        val body = RollingBodyState(); body.restore(RollingBodyState.Pose(0.4, 0.1, 0.2, 0.8))
        assertFalse(body.frame(1000).moving)
        assertEquals(0.8, body.frame(1000).pose.travelledM, 0.0001)
        assertThrows(IllegalArgumentException::class.java) { body.restore(RollingBodyState.Pose(Double.NaN)) }
    }
}
