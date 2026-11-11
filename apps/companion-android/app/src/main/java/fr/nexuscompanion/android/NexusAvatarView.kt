package fr.nexuscompanion.android

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import androidx.annotation.ColorRes
import androidx.annotation.StringRes

internal enum class CompanionPresence(@StringRes val label: Int, @ColorRes val accent: Int) {
    READY(R.string.home_presence_ready, R.color.home_muted_text),
    LISTENING(R.string.home_presence_listening, R.color.home_mint),
    THINKING(R.string.home_presence_thinking, R.color.home_violet),
    SPEAKING(R.string.home_presence_speaking, R.color.home_sky)
}

/** Decorative vector portrait. The adjacent text describes the state for accessibility. */
class NexusAvatarView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {
    private companion object {
        // The silhouette includes the ears. The halo and orbits can extend beyond it.
        const val FACE_WIDTH = 152f
        const val FACE_HEIGHT = 110f
    }

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeCap = Paint.Cap.ROUND }
    private val smile = Path().apply {
        moveTo(140f, 98f)
        quadTo(150f, 105f, 160f, 98f)
    }
    private val shell = LinearGradient(0f, 23f, 0f, 133f, 0xFF32999A.toInt(), 0xFF0B515D.toInt(), Shader.TileMode.CLAMP)
    private val visor = LinearGradient(0f, 37f, 0f, 121f, 0xFFF5F6E9.toInt(), 0xFFD5E5DE.toInt(), Shader.TileMode.CLAMP)
    private val body = LinearGradient(98f, 145f, 202f, 278f, 0xFF2B9090.toInt(), 0xFF0A4C58.toInt(), Shader.TileMode.CLAMP)
    private var accent = context.getColor(CompanionPresence.READY.accent)
    private var halo = haloFor(accent)
    private val faceFrame = RectF()
    private val exitArea = RectF()
    private val faceGutter = 8f * resources.displayMetrics.density
    private var presenceTop = Float.POSITIVE_INFINITY
    private var visibleForMotion = false
    private var eyesClosed = false
    private var mouthOpening = 0.55f
    private val blink = Runnable { closeEyes() }
    private val reopen = Runnable { reopenEyes() }
    private val speechAnimation = ValueAnimator.ofFloat(0.25f, 1f).apply {
        duration = 320L
        repeatMode = ValueAnimator.REVERSE
        repeatCount = ValueAnimator.INFINITE
        addUpdateListener {
            mouthOpening = it.animatedValue as Float
            invalidate()
        }
    }

    internal var presence = CompanionPresence.READY
        set(value) {
            if (value == field) return
            field = value
            accent = context.getColor(value.accent)
            halo = haloFor(accent)
            updateSpeechAnimation()
            invalidate()
        }

    internal var expanded = false
        set(value) {
            field = value
            invalidate()
        }

    internal var showBody = false
        set(value) {
            field = value
            updateFaceFrame()
            invalidate()
        }

    private val portraitHeight get() = if (showBody) 294f else FACE_HEIGHT

    internal fun setFaceControls(exitLeft: Int, exitTop: Int, exitRight: Int, exitBottom: Int, statusTop: Int) {
        exitArea.set(exitLeft - faceGutter, exitTop - faceGutter, exitRight + faceGutter, exitBottom + faceGutter)
        presenceTop = statusTop.toFloat()
        updateFaceFrame()
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        updateFaceFrame()
    }

    private fun updateFaceFrame() {
        val bottom = minOf(height.toFloat(), presenceTop) - faceGutter
        fitFace(faceGutter, bottom)
        // A wide screen can use the height next to the exit button.
        if (RectF.intersects(faceFrame, exitArea)) fitFace(exitArea.bottom, bottom)
    }

    private fun fitFace(top: Float, bottom: Float) {
        val scale = minOf((width - 2f * faceGutter) / FACE_WIDTH, (bottom - top) / portraitHeight)
        if (scale <= 0f) {
            faceFrame.setEmpty()
            return
        }
        val halfWidth = FACE_WIDTH * scale / 2f
        val halfHeight = portraitHeight * scale / 2f
        val centerY = (top + bottom) / 2f
        faceFrame.set(width / 2f - halfWidth, centerY - halfHeight, width / 2f + halfWidth, centerY + halfHeight)
    }

    override fun onVisibilityAggregated(isVisible: Boolean) {
        super.onVisibilityAggregated(isVisible)
        if (visibleForMotion == isVisible) return
        visibleForMotion = isVisible
        if (canAnimate()) postDelayed(blink, 4_600L) else stopBlinking()
        updateSpeechAnimation()
    }

    override fun onDetachedFromWindow() {
        visibleForMotion = false
        stopBlinking()
        updateSpeechAnimation()
        super.onDetachedFromWindow()
    }

    private fun canAnimate() = visibleForMotion && ValueAnimator.areAnimatorsEnabled()

    private fun updateSpeechAnimation() {
        if (presence == CompanionPresence.SPEAKING && canAnimate()) {
            if (!speechAnimation.isStarted) speechAnimation.start()
        } else {
            speechAnimation.cancel()
            mouthOpening = 0.55f
        }
    }

    private fun closeEyes() {
        if (!canAnimate()) return
        eyesClosed = true
        invalidate()
        postDelayed(reopen, 120L)
    }

    private fun reopenEyes() {
        eyesClosed = false
        invalidate()
        if (canAnimate()) postDelayed(blink, 4_600L)
    }

    private fun stopBlinking() {
        removeCallbacks(blink)
        removeCallbacks(reopen)
        if (eyesClosed) {
            eyesClosed = false
            invalidate()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val scale = if (expanded) faceFrame.width() / FACE_WIDTH else minOf(width / 300f, height / if (showBody) 340f else 156f)
        if (scale <= 0f) return
        val centerX = width / 2f
        val centerY = if (expanded) faceFrame.centerY() else height / 2f
        val saved = canvas.save()
        canvas.translate(centerX - 150f * scale, centerY - (if (showBody) 170f else 78f) * scale)
        canvas.scale(scale, scale)

        shade(halo)
        if (expanded) canvas.drawPaint(paint) else canvas.drawRect(0f, 0f, 300f, 156f, paint)
        stroke(accentWithAlpha(accent, 40), 1f)
        canvas.drawOval(35f, 13f, 265f, 144f, paint)
        canvas.drawOval(54f, 4f, 246f, 151f, paint)
        stroke(accentWithAlpha(accent, 135), 1.4f)
        canvas.drawArc(35f, 13f, 265f, 144f, 190f, 42f, false, paint)
        canvas.drawArc(35f, 13f, 265f, 144f, 7f, 29f, false, paint)
        fill(accentWithAlpha(accent, 188))
        canvas.drawCircle(52f, 44f, 2.5f, paint)
        canvas.drawCircle(245f, 116f, 3f, paint)
        canvas.drawCircle(226f, 21f, 1.7f, paint)

        if (showBody) drawBody(canvas)
        if (!showBody) {
            fill(0x60060914)
            canvas.drawOval(91f, 130f, 209f, 146f, paint)
        }
        shade(shell)
        canvas.drawRoundRect(74f, 55f, 102f, 105f, 11f, 11f, paint)
        canvas.drawRoundRect(198f, 55f, 226f, 105f, 11f, 11f, paint)
        canvas.drawRoundRect(85f, 23f, 215f, 133f, 42f, 42f, paint)
        stroke(0xFF7B98B5.toInt(), 1.35f)
        canvas.drawRoundRect(85.5f, 23.5f, 214.5f, 132.5f, 41f, 41f, paint)
        shade(visor)
        canvas.drawRoundRect(96f, 36f, 204f, 121f, 32f, 32f, paint)
        stroke(accentWithAlpha(accent, 120), 1.2f)
        canvas.drawRoundRect(96.5f, 36.5f, 203.5f, 120.5f, 31f, 31f, paint)
        fill(accent)
        canvas.drawRoundRect(143f, 28f, 157f, 31f, 1.5f, 1.5f, paint)
        canvas.drawRoundRect(78f, 68f, 81f, 91f, 1.5f, 1.5f, paint)
        canvas.drawRoundRect(219f, 68f, 222f, 91f, 1.5f, 1.5f, paint)

        val eyeHeight = when (presence) {
            CompanionPresence.READY -> 12f
            CompanionPresence.LISTENING -> 15f
            CompanionPresence.THINKING -> 8f
            CompanionPresence.SPEAKING -> 13f
        }
        val gazeOffset = if (presence == CompanionPresence.THINKING) 3f else 0f
        drawEye(canvas, 127f, eyeHeight, gazeOffset)
        drawEye(canvas, 173f, eyeHeight, gazeOffset)
        if (presence == CompanionPresence.SPEAKING) {
            val depth = 3f + 5f * mouthOpening
            fill(0xFF25545B.toInt())
            canvas.drawOval(140f, 99f - depth, 160f, 99f + depth, paint)
            fill(0xFF14243B.toInt())
            canvas.drawOval(143f, 101f - depth, 157f, 97f + depth, paint)
        } else {
            stroke(0xFF25545B.toInt(), 2.6f)
            canvas.drawPath(smile, paint)
        }

        canvas.restoreToCount(saved)
    }

    private fun drawBody(canvas: Canvas) {
        fill(0x60060914)
        canvas.drawOval(87f, 304f, 213f, 326f, paint)
        fill(0xFF0B515D.toInt())
        canvas.drawRoundRect(133f, 120f, 167f, 154f, 10f, 10f, paint)
        canvas.drawRoundRect(108f, 266f, 145f, 316f, 14f, 14f, paint)
        canvas.drawRoundRect(155f, 266f, 192f, 316f, 14f, 14f, paint)
        shade(body)
        canvas.drawRoundRect(75f, 150f, 105f, 244f, 15f, 15f, paint)
        canvas.drawRoundRect(195f, 150f, 225f, 244f, 15f, 15f, paint)
        canvas.drawRoundRect(98f, 136f, 202f, 290f, 42f, 42f, paint)
        stroke(0xFF7AB7B8.toInt(), 1f)
        canvas.drawArc(102f, 139f, 198f, 285f, 205f, 85f, false, paint)
        fill(0xFF0B515D.toInt())
        canvas.drawOval(130f, 237f, 170f, 275f, paint)
        stroke(0xFF468D91.toInt(), 1f)
        for (x in 0..5) canvas.drawLine(136f + x * 5f, 246f, 136f + x * 5f, 266f, paint)
        stroke(context.getColor(R.color.home_mint), 2f)
        canvas.drawLine(140f, 171f, 160f, 171f, paint)
        canvas.drawLine(150f, 171f, 150f, 178f, paint)
    }

    private fun drawEye(canvas: Canvas, x: Float, height: Float, gazeOffset: Float) {
        if (eyesClosed) {
            stroke(accent, 2.7f)
            canvas.drawLine(x - 8f, 72f, x + 8f, 72f, paint)
            return
        }
        fill(accentWithAlpha(accent, 45))
        canvas.drawRoundRect(x - 13f, 72f - height - 2f, x + 13f, 72f + height + 2f, 10f, 10f, paint)
        fill(0xFF93DBAC.toInt())
        canvas.drawRoundRect(x - 9.5f, 72f - height, x + 9.5f, 72f + height, 8f, 8f, paint)
        val pupilX = x + gazeOffset
        val pupilRadiusY = height * 0.55f
        fill(0xFF14243B.toInt())
        canvas.drawOval(pupilX - 4.5f, 72f - pupilRadiusY, pupilX + 4.5f, 72f + pupilRadiusY, paint)
        fill(0xEFFFFFFF.toInt())
        canvas.drawCircle(pupilX - 1.4f, 72f - pupilRadiusY * 0.46f, 1.5f, paint)
    }

    private fun fill(color: Int) {
        paint.shader = null
        paint.style = Paint.Style.FILL
        paint.color = color
    }

    private fun stroke(color: Int, width: Float) {
        paint.shader = null
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = width
        paint.color = color
    }

    private fun shade(shader: Shader) {
        paint.style = Paint.Style.FILL
        paint.color = Color.WHITE
        paint.shader = shader
    }

    private fun haloFor(color: Int) = RadialGradient(150f, 78f, 112f, accentWithAlpha(color, 62), Color.TRANSPARENT, Shader.TileMode.CLAMP)

    private fun accentWithAlpha(color: Int, alpha: Int) = Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color))
}
