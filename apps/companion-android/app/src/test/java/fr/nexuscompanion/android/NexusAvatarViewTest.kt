package fr.nexuscompanion.android

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.os.Looper
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.LooperMode
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
class NexusAvatarViewTest {
    @Test fun bothPupilsRenderInEveryPresenceAndMoveTogetherWhileThinking() {
        val avatar = NexusAvatarView(RuntimeEnvironment.getApplication())
        for (presence in CompanionPresence.entries) {
            avatar.presence = presence
            avatar.expanded = false
            val home = render(avatar, 300, 156)
            avatar.expanded = true
            val fullFace = render(avatar, 376, 312)
            // In the full-face frame the same portrait is doubled and centered.
            val gaze = if (presence == CompanionPresence.THINKING) 6 else 0
            for (eyeX in listOf(127, 173)) {
                assertDarkPupil(home, eyeX + gaze, eyeX - 7, 72)
                val expandedEyeX = eyeX * 2 - 112
                assertDarkPupil(fullFace, expandedEyeX + gaze * 2, expandedEyeX - 14, 144)
            }
        }
    }

    @Test fun aBlinkHidesAndRestoresBothPupils() {
        val lifecycle = Robolectric.buildActivity(Activity::class.java).setup()
        try {
            val activity = lifecycle.get()
            val avatar = NexusAvatarView(activity)
            activity.setContentView(avatar)
            avatar.onVisibilityAggregated(true)
            val looper = shadowOf(Looper.getMainLooper())
            looper.idleFor(Duration.ofMillis(4_600))
            val closed = render(avatar, 300, 156)
            looper.idleFor(Duration.ofMillis(120))
            val reopened = render(avatar, 300, 156)

            for (eyeX in listOf(127, 173)) {
                assertTrue(Color.luminance(closed.getPixel(eyeX, 72)) > 0.4f)
                assertDarkPupil(reopened, eyeX, eyeX - 7, 72)
            }
        } finally {
            lifecycle.pause().stop().destroy()
        }
    }

    private fun render(avatar: NexusAvatarView, width: Int, height: Int): Bitmap {
        avatar.layout(0, 0, width, height)
        return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also {
            avatar.draw(Canvas(it))
        }
    }

    private fun assertDarkPupil(bitmap: Bitmap, pupilX: Int, eyeX: Int, y: Int) {
        val pupil = Color.luminance(bitmap.getPixel(pupilX, y))
        val eye = Color.luminance(bitmap.getPixel(eyeX, y))
        assertTrue("Expected a dark pupil inside a bright eye", eye > 0.4f && pupil < eye / 2)
    }
}
