package fr.nexuscompanion.android

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
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
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeCap = Paint.Cap.ROUND }
    private val smile = Path().apply {
        moveTo(140f, 98f)
        quadTo(150f, 105f, 160f, 98f)
    }
    private val shell = LinearGradient(0f, 23f, 0f, 133f, 0xFF4A6289.toInt(), 0xFF233855.toInt(), Shader.TileMode.CLAMP)
    private val visor = LinearGradient(0f, 37f, 0f, 121f, 0xFF1D3550.toInt(), 0xFF0B1425.toInt(), Shader.TileMode.CLAMP)
    private var accent = context.getColor(CompanionPresence.READY.accent)
    private var halo = haloFor(accent)
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
        val scale = minOf(width / 300f, height / 156f)
        if (scale <= 0f) return
        val saved = canvas.save()
        canvas.translate((width - 300f * scale) / 2f, (height - 156f * scale) / 2f)
        canvas.scale(scale, scale)

        shade(halo)
        canvas.drawRect(0f, 0f, 300f, 156f, paint)
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

        fill(0x60060914)
        canvas.drawOval(91f, 130f, 209f, 146f, paint)
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
        val gaze = if (presence == CompanionPresence.THINKING) 3f else 0f
        drawEye(canvas, 127f + gaze, eyeHeight)
        drawEye(canvas, 173f + gaze, eyeHeight)
        if (presence == CompanionPresence.SPEAKING) {
            val depth = 3f + 5f * mouthOpening
            fill(accent)
            canvas.drawOval(140f, 99f - depth, 160f, 99f + depth, paint)
            fill(0xFF14243B.toInt())
            canvas.drawOval(143f, 101f - depth, 157f, 97f + depth, paint)
        } else {
            stroke(accent, 2.6f)
            canvas.drawPath(smile, paint)
        }

        canvas.restoreToCount(saved)
    }

    private fun drawEye(canvas: Canvas, x: Float, height: Float) {
        if (eyesClosed) {
            stroke(accent, 2.7f)
            canvas.drawLine(x - 8f, 72f, x + 8f, 72f, paint)
            return
        }
        fill(accentWithAlpha(accent, 45))
        canvas.drawRoundRect(x - 13f, 72f - height - 2f, x + 13f, 72f + height + 2f, 10f, 10f, paint)
        fill(accent)
        canvas.drawRoundRect(x - 9.5f, 72f - height, x + 9.5f, 72f + height, 8f, 8f, paint)
        fill(0xCFFFFFFF.toInt())
        canvas.drawCircle(x - 3f, 74f - height, 2.4f, paint)
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
