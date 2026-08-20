package com.trezviymir.spacewallpaper

import android.app.Activity
import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(28), dp(36), dp(28), dp(28))
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(Color.rgb(8, 7, 28), Color.rgb(2, 3, 10), Color.BLACK)
            )
        }

        content.addView(ImageView(this).apply {
            setImageResource(com.trezviymir.spacewallpaper.R.drawable.brand_icon)
            scaleType = ImageView.ScaleType.CENTER_CROP
        }, LinearLayout.LayoutParams(dp(170), dp(170)).apply {
            bottomMargin = dp(24)
        })

        content.addView(TextView(this).apply {
            text = getString(R.string.home_brand)
            setTextColor(Color.WHITE)
            textSize = 32f
            gravity = Gravity.CENTER
            typeface = android.graphics.Typeface.create("sans-serif-light", android.graphics.Typeface.NORMAL)
            letterSpacing = 0.025f
        })

        content.addView(TextView(this).apply {
            text = getString(R.string.home_kicker)
            setTextColor(Color.rgb(79, 211, 255))
            textSize = 12f
            gravity = Gravity.CENTER
            letterSpacing = 0.14f
            setPadding(0, dp(8), 0, dp(34))
        })

        content.addView(TextView(this).apply {
            text = getString(R.string.home_description)
            setTextColor(Color.rgb(196, 201, 221))
            textSize = 16f
            gravity = Gravity.CENTER
            setLineSpacing(0f, 1.18f)
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))

        content.addView(Button(this).apply {
            text = getString(R.string.install_wallpaper)
            textSize = 17f
            isAllCaps = false
            setTextColor(Color.BLACK)
            background = roundedGradient(
                intArrayOf(Color.rgb(57, 226, 255), Color.rgb(156, 82, 255)),
                dp(18).toFloat()
            )
            setOnClickListener { openWallpaperPreview() }
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(58)).apply {
            topMargin = dp(24)
        })

        content.addView(TextView(this).apply {
            text = getString(R.string.lock_screen_note)
            setTextColor(Color.rgb(126, 133, 157))
            textSize = 12f
            gravity = Gravity.CENTER
            setPadding(0, dp(18), 0, 0)
        })

        setContentView(content)
    }

    private fun openWallpaperPreview() {
        val component = ComponentName(this, TrezviyMirWallpaperService::class.java)
        val direct = Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER).apply {
            putExtra(WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT, component)
        }
        try {
            startActivity(direct)
        } catch (_: Exception) {
            startActivity(Intent(WallpaperManager.ACTION_LIVE_WALLPAPER_CHOOSER))
        }
    }

    private fun roundedGradient(colors: IntArray, radius: Float) = GradientDrawable(
        GradientDrawable.Orientation.LEFT_RIGHT,
        colors
    ).apply { cornerRadius = radius }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
