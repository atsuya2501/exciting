package com.atsuya.dailydashboard

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

class YouTubeGateActivity : Activity() {
    private lateinit var target: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        target = intent.getStringExtra(EXTRA_TARGET) ?: YouTubeGateAccessibilityService.TARGET_YOUTUBE
        if (TimerScheduler.status(this) == "running" || isTemporarilyAllowed(this, target)) {
            openTarget()
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
            text = if (target == YouTubeGateAccessibilityService.TARGET_INSTAGRAM) "Instagramで何を見る？" else "YouTubeを開きますか？"
            textSize = 25f
            setTextColor(Color.WHITE)
            setTypeface(typeface, Typeface.BOLD)
            gravity = Gravity.CENTER
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        root.addView(TextView(this).apply {
            text = if (target == YouTubeGateAccessibilityService.TARGET_INSTAGRAM) "目的を検索してから開きます" else "いまはポモドーロが動いていません"
            textSize = 15f
            setTextColor(Color.rgb(185, 190, 200))
            gravity = Gravity.CENTER
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(10); bottomMargin = dp(30)
        })

        if (target == YouTubeGateAccessibilityService.TARGET_INSTAGRAM) {
            val reason = EditText(this).apply {
                hint = "例：英語の発音、店名、人物名"
                textSize = 17f
                setTextColor(Color.WHITE)
                setHintTextColor(Color.rgb(135, 140, 150))
                setSingleLine(true)
                inputType = InputType.TYPE_CLASS_TEXT
                setPadding(dp(14), 0, dp(14), 0)
                setBackgroundColor(Color.rgb(40, 43, 51))
            }
            root.addView(reason, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(56)).apply {
                bottomMargin = dp(12)
            })
            root.addView(actionButton("検索して開く", Color.rgb(216, 74, 58)) {
                val query = reason.text.toString().trim()
                if (query.isBlank()) {
                    reason.error = "見る目的を入力してください"
                    reason.requestFocus()
                } else {
                    allowTemporarily(this, target, 2 * 60 * 1000L)
                    openInstagramSearch(query)
                }
            })
        } else {
            root.addView(actionButton("25分集中を始める", Color.rgb(216, 74, 58)) {
                TimerScheduler.schedulePreset(this, "work")
                Toast.makeText(this, "${TimerScheduler.preferredFocus(this)}・25分を開始しました", Toast.LENGTH_SHORT).show()
                openTarget()
            })
            root.addView(actionButton("今回はそのまま開く", Color.rgb(55, 59, 69)) {
                allowTemporarily(this, target, ALLOW_MILLIS)
                openTarget()
            }.apply {
                (layoutParams as? LinearLayout.LayoutParams)?.topMargin = dp(12)
            })
        }
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

    private fun openTarget() {
        val packageName = if (target == YouTubeGateAccessibilityService.TARGET_INSTAGRAM) {
            YouTubeGateAccessibilityService.INSTAGRAM_PACKAGE
        } else {
            YouTubeGateAccessibilityService.YOUTUBE_PACKAGE
        }
        packageManager.getLaunchIntentForPackage(packageName)?.let {
            it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
            startActivity(it)
        }
        finish()
    }

    private fun openInstagramSearch(query: String) {
        val searchIntent = Intent(
            Intent.ACTION_VIEW,
            Uri.parse("https://www.instagram.com/explore/search/keyword/?q=${Uri.encode(query)}")
        ).apply {
            setPackage(YouTubeGateAccessibilityService.INSTAGRAM_PACKAGE)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            startActivity(searchIntent)
        } catch (_: Exception) {
            openTarget()
            return
        }
        finish()
    }

    companion object {
        private const val PREFS = "youtube_gate"
        private const val ALLOW_MILLIS = 10 * 60 * 1000L
        const val EXTRA_TARGET = "target"

        fun isTemporarilyAllowed(context: Context, target: String): Boolean =
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getLong("allowed_until_$target", 0L) > System.currentTimeMillis()

        private fun allowTemporarily(context: Context, target: String, durationMillis: Long) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putLong("allowed_until_$target", System.currentTimeMillis() + durationMillis)
                .apply()
        }
    }
}
