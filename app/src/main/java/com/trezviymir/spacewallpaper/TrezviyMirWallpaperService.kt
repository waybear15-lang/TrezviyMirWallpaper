package com.trezviymir.spacewallpaper

import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.service.wallpaper.WallpaperService
import android.view.SurfaceHolder
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

class TrezviyMirWallpaperService : WallpaperService() {
    override fun onCreateEngine(): Engine = SpaceEngine()

    inner class SpaceEngine : Engine() {
        private val handler = Handler(Looper.getMainLooper())
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG)
        private var visible = false
        private val stars = createStars(42)

        private val frameRunner = object : Runnable {
            override fun run() {
                drawFrame()
                if (visible) handler.postDelayed(this, FRAME_DELAY_MS)
            }
        }

        override fun onVisibilityChanged(isVisible: Boolean) {
            visible = isVisible
            handler.removeCallbacks(frameRunner)
            if (visible) frameRunner.run()
        }

        override fun onSurfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
            drawFrame()
        }

        override fun onSurfaceDestroyed(holder: SurfaceHolder) {
            visible = false
            handler.removeCallbacks(frameRunner)
        }

        override fun onDestroy() {
            handler.removeCallbacks(frameRunner)
            super.onDestroy()
        }

        private fun drawFrame() {
            var canvas: Canvas? = null
            try {
                canvas = surfaceHolder.lockCanvas() ?: return
                render(canvas, System.currentTimeMillis())
            } finally {
                canvas?.let(surfaceHolder::unlockCanvasAndPost)
            }
        }

        private fun render(canvas: Canvas, nowMs: Long) {
            val w = canvas.width.toFloat()
            val h = canvas.height.toFloat()
            val phase = (nowMs % 18_000L) / 18_000f
            canvas.drawColor(Color.rgb(0, 1, 5))
            drawBackgroundAura(canvas, w, h)
            drawStars(canvas, w, h, nowMs)
            drawPlanet(canvas, w, h, phase)
            drawBrand(canvas, w, h)
        }

        private fun drawBackgroundAura(canvas: Canvas, w: Float, h: Float) {
            paint.style = Paint.Style.FILL
            paint.shader = RadialGradient(
                w * 0.5f,
                h * 0.46f,
                min(w, h) * 0.58f,
                intArrayOf(Color.argb(38, 21, 11, 72), Color.argb(12, 0, 65, 110), Color.TRANSPARENT),
                floatArrayOf(0f, 0.48f, 1f),
                Shader.TileMode.CLAMP
            )
            canvas.drawRect(0f, 0f, w, h, paint)
            paint.shader = null
        }

        private fun drawStars(canvas: Canvas, w: Float, h: Float, nowMs: Long) {
            paint.style = Paint.Style.FILL
            paint.maskFilter = null
            stars.forEachIndexed { index, star ->
                val pulse = 0.55f + 0.45f * sin(nowMs / 900.0 + index * 1.73).toFloat()
                paint.color = Color.argb((90 + pulse * 165).toInt(), 213, 235, 255)
                val radius = w * star.size * (0.78f + pulse * 0.22f)
                canvas.drawCircle(w * star.x, h * star.y, radius, paint)
            }
        }

        private fun drawPlanet(canvas: Canvas, w: Float, h: Float, phase: Float) {
            val radius = min(w * 0.43f, h * 0.205f)
            val cx = w * 0.5f
            val cy = h * 0.535f
            val bounds = RectF(cx - radius, cy - radius, cx + radius, cy + radius)

            paint.style = Paint.Style.FILL
            paint.shader = RadialGradient(
                cx,
                cy - radius * 0.55f,
                radius * 1.25f,
                intArrayOf(Color.rgb(14, 12, 48), Color.rgb(4, 5, 20), Color.rgb(0, 1, 6)),
                floatArrayOf(0f, 0.58f, 1f),
                Shader.TileMode.CLAMP
            )
            canvas.drawCircle(cx, cy, radius, paint)
            paint.shader = null

            // Subtle geometric grid gives this brand a globe rather than an eclipse.
            canvas.save()
            canvas.clipPath(Path().apply { addCircle(cx, cy, radius * 0.992f, Path.Direction.CW) })
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = max(1f, w * 0.0015f)
            paint.color = Color.argb(72, 74, 139, 255)
            for (scale in floatArrayOf(0.34f, 0.68f)) {
                canvas.drawOval(
                    RectF(cx - radius, cy - radius * scale, cx + radius, cy + radius * scale),
                    paint
                )
            }
            paint.color = Color.argb(62, 186, 75, 255)
            for (scale in floatArrayOf(0.38f, 0.72f)) {
                canvas.drawOval(
                    RectF(cx - radius * scale, cy - radius, cx + radius * scale, cy + radius),
                    paint
                )
            }
            canvas.restore()

            // Wide glow under the razor-sharp cyan-to-magenta horizon.
            paint.style = Paint.Style.STROKE
            paint.strokeCap = Paint.Cap.ROUND
            paint.strokeWidth = w * 0.022f
            paint.maskFilter = BlurMaskFilter(w * 0.024f, BlurMaskFilter.Blur.NORMAL)
            paint.shader = LinearGradient(
                cx - radius, cy, cx + radius, cy,
                intArrayOf(Color.rgb(0, 187, 255), Color.rgb(56, 96, 255), Color.rgb(239, 54, 255)),
                null,
                Shader.TileMode.CLAMP
            )
            canvas.drawArc(bounds, 180f, 180f, false, paint)

            paint.maskFilter = null
            paint.strokeWidth = max(2.2f, w * 0.006f)
            canvas.drawArc(bounds, 180f, 180f, false, paint)
            paint.shader = null

            paint.strokeWidth = max(1.5f, w * 0.003f)
            paint.shader = LinearGradient(
                0f, cy, w, cy,
                intArrayOf(Color.TRANSPARENT, Color.rgb(0, 211, 255), Color.rgb(123, 85, 255), Color.rgb(255, 58, 242), Color.TRANSPARENT),
                floatArrayOf(0f, 0.18f, 0.5f, 0.82f, 1f),
                Shader.TileMode.CLAMP
            )
            canvas.drawLine(0f, cy, w, cy, paint)
            paint.shader = null

            drawOrbitingFlare(canvas, cx, cy, radius, phase, w)
        }

        private fun drawOrbitingFlare(canvas: Canvas, cx: Float, cy: Float, radius: Float, phase: Float, w: Float) {
            val angle = PI + PI * phase
            val x = cx + cos(angle).toFloat() * radius
            val y = cy + sin(angle).toFloat() * radius
            paint.style = Paint.Style.FILL
            paint.shader = RadialGradient(
                x, y, w * 0.075f,
                intArrayOf(Color.WHITE, Color.argb(210, 255, 91, 251), Color.TRANSPARENT),
                floatArrayOf(0f, 0.17f, 1f),
                Shader.TileMode.CLAMP
            )
            canvas.drawCircle(x, y, w * 0.075f, paint)
            paint.shader = null
        }

        private fun drawBrand(canvas: Canvas, w: Float, h: Float) {
            paint.style = Paint.Style.FILL
            paint.textAlign = Paint.Align.CENTER
            paint.typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
            paint.textSize = fittedTextSize("Трезвый Мир", w * 0.86f, w * 0.115f)
            paint.shader = LinearGradient(
                w * 0.12f, 0f, w * 0.88f, 0f,
                intArrayOf(Color.rgb(62, 232, 255), Color.rgb(44, 136, 255), Color.rgb(209, 72, 255)),
                null,
                Shader.TileMode.CLAMP
            )
            canvas.drawText("Трезвый Мир", w * 0.5f, h * 0.675f, paint)
            paint.shader = null

            paint.typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            paint.textSize = w * 0.025f
            paint.letterSpacing = 0.15f
            paint.color = Color.argb(150, 151, 197, 229)
            canvas.drawText("КОСМИЧЕСКАЯ ЗАСТАВКА", w * 0.5f, h * 0.705f, paint)
            paint.letterSpacing = 0f
            paint.textAlign = Paint.Align.LEFT
        }

        private fun fittedTextSize(text: String, maxWidth: Float, preferred: Float): Float {
            paint.textSize = preferred
            val measured = paint.measureText(text)
            return if (measured <= maxWidth) preferred else preferred * maxWidth / measured
        }

        private fun createStars(count: Int): List<Star> {
            var seed = 0x51A7C0DEL
            fun next(): Float {
                seed = (seed * 1_103_515_245L + 12_345L) and 0x7fffffffL
                return seed.toFloat() / 0x7fffffffL.toFloat()
            }
            return List(count) {
                Star(
                    x = 0.055f + next() * 0.89f,
                    y = 0.035f + next() * 0.39f,
                    size = 0.0012f + next() * 0.0022f
                )
            }
        }
    }

    private data class Star(val x: Float, val y: Float, val size: Float)

    private companion object {
        const val FRAME_DELAY_MS = 50L
    }
}

