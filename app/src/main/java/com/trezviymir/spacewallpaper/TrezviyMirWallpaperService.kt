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
import java.time.LocalTime
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
        private val stars = createStars(18)

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
            val time = LocalTime.now()
            val secondsOfDay = time.toSecondOfDay() + time.nano / 1_000_000_000f
            // 06:00 is sunrise on the right/east edge. Negative screen-space
            // angles move counter-clockwise: east -> zenith -> west -> nadir -> east.
            val orbitPhase = ((secondsOfDay - 6f * 3600f + DAY_SECONDS) % DAY_SECONDS) / DAY_SECONDS
            val rotationPhase = (nowMs % PLANET_ROTATION_MS) / PLANET_ROTATION_MS.toFloat()
            canvas.drawColor(Color.rgb(0, 1, 5))
            drawBackgroundAura(canvas, w, h)
            drawStars(canvas, w, h, nowMs)
            drawPlanet(canvas, w, h, orbitPhase, rotationPhase)
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

        private fun drawPlanet(
            canvas: Canvas,
            w: Float,
            h: Float,
            orbitPhase: Float,
            rotationPhase: Float
        ) {
            // The reference uses a wide, slightly flattened globe: roughly one
            // third of the screen width and fifteen percent of its height.
            val radiusX = w * 0.33f
            val radiusY = h * 0.155f
            val cx = w * 0.5f
            val cy = h * 0.525f
            val bounds = RectF(cx - radiusX, cy - radiusY, cx + radiusX, cy + radiusY)
            val orbitAngle = -2.0 * PI * orbitPhase
            val lightX = cx + cos(orbitAngle).toFloat() * radiusX
            val lightY = cy + sin(orbitAngle).toFloat() * radiusY
            val lightColor = blendColor(
                Color.rgb(15, 205, 255),
                Color.rgb(255, 58, 242),
                ((lightX - (cx - radiusX)) / (radiusX * 2f)).coerceIn(0f, 1f)
            )

            // Draw the broad aura first. The black globe masks its inner half,
            // so the light reads as travelling around the planet rather than across it.
            drawOrbitGlow(canvas, lightX, lightY, lightColor, w, drawCore = false)

            paint.style = Paint.Style.FILL
            paint.shader = RadialGradient(
                cx,
                cy - radiusY * 0.55f,
                max(radiusX, radiusY) * 1.25f,
                intArrayOf(Color.rgb(8, 9, 32), Color.rgb(2, 3, 14), Color.rgb(0, 1, 5)),
                floatArrayOf(0f, 0.58f, 1f),
                Shader.TileMode.CLAMP
            )
            canvas.drawOval(bounds, paint)
            paint.shader = null

            // Only the upper grid is visible, matching the reference hemisphere.
            // Meridians are projected from rotating longitudes, so the planet turns
            // clockwise around its own vertical axis once every 12 minutes.
            canvas.save()
            canvas.clipPath(Path().apply { addOval(bounds, Path.Direction.CW) })
            canvas.clipRect(cx - radiusX, cy - radiusY, cx + radiusX, cy + 1f)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = max(1f, w * 0.0017f)
            paint.color = Color.argb(88, 58, 135, 255)
            for (scale in floatArrayOf(0.34f, 0.68f)) {
                canvas.drawOval(
                    RectF(cx - radiusX, cy - radiusY * scale, cx + radiusX, cy + radiusY * scale),
                    paint
                )
            }

            paint.color = Color.argb(90, 132, 72, 255)
            val rotation = -rotationPhase * 2f * PI.toFloat()
            for (longitudeIndex in 0 until 8) {
                val longitude = longitudeIndex * PI.toFloat() / 4f + rotation
                if (cos(longitude) <= 0f) continue
                val path = Path()
                for (step in 0..36) {
                    val latitude = step / 36f * PI.toFloat() / 2f
                    val x = cx + radiusX * cos(latitude) * sin(longitude)
                    val y = cy - radiusY * sin(latitude)
                    if (step == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                canvas.drawPath(path, paint)
            }
            canvas.restore()

            // Bright reference-style cyan-to-magenta upper rim.
            paint.style = Paint.Style.STROKE
            paint.strokeCap = Paint.Cap.ROUND
            paint.strokeWidth = w * 0.019f
            paint.maskFilter = BlurMaskFilter(w * 0.021f, BlurMaskFilter.Blur.NORMAL)
            paint.shader = LinearGradient(
                cx - radiusX, cy, cx + radiusX, cy,
                intArrayOf(Color.rgb(0, 220, 255), Color.rgb(62, 101, 255), Color.rgb(255, 59, 247)),
                null,
                Shader.TileMode.CLAMP
            )
            canvas.drawArc(bounds, 180f, 180f, false, paint)

            paint.maskFilter = null
            paint.strokeWidth = max(2.4f, w * 0.0056f)
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

            drawOrbitGlow(canvas, lightX, lightY, lightColor, w, drawCore = true)
        }

        private fun drawOrbitGlow(
            canvas: Canvas,
            x: Float,
            y: Float,
            color: Int,
            w: Float,
            drawCore: Boolean
        ) {
            paint.style = Paint.Style.FILL
            paint.maskFilter = null
            val glowRadius = if (drawCore) w * 0.046f else w * 0.095f
            paint.shader = RadialGradient(
                x, y, glowRadius,
                if (drawCore) {
                    intArrayOf(Color.WHITE, withAlpha(color, 238), Color.TRANSPARENT)
                } else {
                    intArrayOf(withAlpha(Color.WHITE, 210), withAlpha(color, 150), Color.TRANSPARENT)
                },
                if (drawCore) floatArrayOf(0f, 0.12f, 1f) else floatArrayOf(0f, 0.16f, 1f),
                Shader.TileMode.CLAMP
            )
            canvas.drawCircle(x, y, glowRadius, paint)
            paint.shader = null

            if (drawCore) {
                paint.style = Paint.Style.STROKE
                paint.strokeCap = Paint.Cap.ROUND
                paint.strokeWidth = max(1f, w * 0.0017f)
                paint.color = withAlpha(Color.WHITE, 185)
                paint.maskFilter = BlurMaskFilter(w * 0.006f, BlurMaskFilter.Blur.NORMAL)
                canvas.drawLine(x - w * 0.018f, y, x + w * 0.018f, y, paint)
                canvas.drawLine(x, y - w * 0.013f, x, y + w * 0.013f, paint)
                paint.maskFilter = null
                paint.style = Paint.Style.FILL
                paint.color = Color.WHITE
                canvas.drawCircle(x, y, max(1.8f, w * 0.0045f), paint)
            }
        }

        private fun drawBrand(canvas: Canvas, w: Float, h: Float) {
            paint.style = Paint.Style.FILL
            paint.textAlign = Paint.Align.CENTER
            paint.typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
            paint.textSize = fittedTextSize("Трезвый Мир", w * 0.92f, w * 0.148f)
            paint.shader = LinearGradient(
                w * 0.08f, 0f, w * 0.92f, 0f,
                intArrayOf(Color.rgb(64, 247, 255), Color.rgb(28, 151, 255), Color.rgb(111, 91, 255)),
                null,
                Shader.TileMode.CLAMP
            )
            paint.maskFilter = BlurMaskFilter(w * 0.013f, BlurMaskFilter.Blur.NORMAL)
            canvas.drawText("Трезвый Мир", w * 0.5f, h * 0.615f, paint)
            paint.maskFilter = null
            canvas.drawText("Трезвый Мир", w * 0.5f, h * 0.615f, paint)
            paint.shader = null
            paint.textAlign = Paint.Align.LEFT
        }

        private fun withAlpha(color: Int, alpha: Int): Int = Color.argb(
            alpha.coerceIn(0, 255),
            Color.red(color),
            Color.green(color),
            Color.blue(color)
        )

        private fun blendColor(from: Int, to: Int, amount: Float): Int {
            val t = amount.coerceIn(0f, 1f)
            return Color.rgb(
                (Color.red(from) + (Color.red(to) - Color.red(from)) * t).toInt(),
                (Color.green(from) + (Color.green(to) - Color.green(from)) * t).toInt(),
                (Color.blue(from) + (Color.blue(to) - Color.blue(from)) * t).toInt()
            )
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
        const val FRAME_DELAY_MS = 100L
        const val DAY_SECONDS = 86_400f
        const val PLANET_ROTATION_MS = 12L * 60L * 1000L
    }
}
