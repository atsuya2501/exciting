package com.atsuya.dailydashboard

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

class YouTubeGateActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (TimerScheduler.status(this) == "running" || isTemporarilyAllowed(this)) {
            openYouTube()
            return
        }

        val density = resources.displayMetrics.density
        fun dp(value: Int) = (value * density).toInt()
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(28), dp(32), dp(28), dp(32))
            setBackgroundColor(Color.rgb(18, 20, 26))
        }
        root.addView(TextView(this).apply {
            text = "YouTubeを開きますか？"
            textSize = 25f
            setTextColor(Color.WHITE)
            setTypeface(typeface, Typeface.BOLD)
            gravity = Gravity.CENTER
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        root.addView(TextView(this).apply {
            text = "いまはポモドーロが動いていません"
            textSize = 15f
            setTextColor(Color.rgb(185, 190, 200))
            gravity = Gravity.CENTER
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(10); bottomMargin = dp(30)
        })

        root.addView(actionButton("25分集中を始める", Color.rgb(216, 74, 58)) {
            TimerScheduler.schedulePreset(this, "work")
            Toast.makeText(this, "${TimerScheduler.preferredFocus(this)}・25分を開始しました", Toast.LENGTH_SHORT).show()
            openYouTube()
        })
        root.addView(actionButton("今回はそのまま開く", Color.rgb(55, 59, 69)) {
            allowTemporarily(this)
            openYouTube()
        }.apply {
            (layoutParams as? LinearLayout.LayoutParams)?.topMargin = dp(12)
        })
        root.addView(actionButton("やめる", Color.TRANSPARENT) { finish() }.apply {
            (layoutParams as? LinearLayout.LayoutParams)?.topMargin = dp(12)
        })
        setContentView(root)
    }

    private fun actionButton(label: String, color: Int, action: () -> Unit) = Button(this).apply {
        text = label
        textSize = 17f
        isAllCaps = false
        setTextColor(Color.WHITE)
        setBackgroundColor(color)
        setOnClickListener { action() }
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, resources.displayMetrics.density.times(56).toInt())
    }

    private fun openYouTube() {
        packageManager.getLaunchIntentForPackage(YouTubeGateAccessibilityService.YOUTUBE_PACKAGE)?.let {
            it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
            startActivity(it)
        }
        finish()
    }

    companion object {
        private const val PREFS = "youtube_gate"
        private const val KEY_ALLOWED_UNTIL = "allowed_until"
        private const val ALLOW_MILLIS = 10 * 60 * 1000L

        fun isTemporarilyAllowed(context: Context): Boolean =
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getLong(KEY_ALLOWED_UNTIL, 0L) > System.currentTimeMillis()

        private fun allowTemporarily(context: Context) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putLong(KEY_ALLOWED_UNTIL, System.currentTimeMillis() + ALLOW_MILLIS)
                .apply()
        }
    }
}
