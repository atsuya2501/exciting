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

/** クラス名は既存のManifestとユーザー補助許可を引き継ぐため維持している。 */
class YouTubeGateActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (isTemporarilyAllowed(this)) {
            finish()
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
            text = "Instagramで検索"
            textSize = 25f
            setTextColor(Color.WHITE)
            setTypeface(typeface, Typeface.BOLD)
            gravity = Gravity.CENTER
        })
        root.addView(TextView(this).apply {
            text = "検索ワードを入力して開きます"
            textSize = 15f
            setTextColor(Color.rgb(185, 190, 200))
            gravity = Gravity.CENTER
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(10); bottomMargin = dp(30)
        })

        val queryInput = EditText(this).apply {
            hint = "検索ワード"
            textSize = 17f
            setTextColor(Color.WHITE)
            setHintTextColor(Color.rgb(135, 140, 150))
            setSingleLine(true)
            inputType = InputType.TYPE_CLASS_TEXT
            setPadding(dp(14), 0, dp(14), 0)
            setBackgroundColor(Color.rgb(40, 43, 51))
        }
        root.addView(queryInput, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(56)).apply {
            bottomMargin = dp(12)
        })
        root.addView(actionButton("検索して開く", Color.rgb(216, 74, 58)) {
            val query = queryInput.text.toString().trim()
            if (query.isBlank()) {
                queryInput.error = "検索ワードを入力してください"
                queryInput.requestFocus()
            } else {
                allowForLaunch(this)
                openInstagramSearch(query)
            }
        })
        root.addView(actionButton("やめる", Color.TRANSPARENT) { goHome() }.apply {
            (layoutParams as? LinearLayout.LayoutParams)?.topMargin = dp(12)
        })
        setContentView(root)
        queryInput.requestFocus()
    }

    private fun actionButton(label: String, color: Int, action: () -> Unit) = Button(this).apply {
        text = label
        textSize = 17f
        isAllCaps = false
        setTextColor(Color.WHITE)
        setBackgroundColor(color)
        setOnClickListener { action() }
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            resources.displayMetrics.density.times(56).toInt()
        )
    }

    private fun openInstagramSearch(query: String) {
        val intent = Intent(
            Intent.ACTION_VIEW,
            Uri.parse("https://www.instagram.com/explore/search/keyword/?q=${Uri.encode(query)}")
        ).apply {
            setPackage(YouTubeGateAccessibilityService.INSTAGRAM_PACKAGE)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            startActivity(intent)
        } catch (_: Exception) {
            packageManager.getLaunchIntentForPackage(YouTubeGateAccessibilityService.INSTAGRAM_PACKAGE)?.let {
                startActivity(it)
            }
        }
        finish()
    }

    private fun goHome() {
        startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME))
        finish()
    }

    companion object {
        private const val PREFS = "instagram_search_gate"
        private const val KEY_ALLOWED_UNTIL = "allowed_until"
        private const val LAUNCH_GRACE_MILLIS = 10_000L

        fun isTemporarilyAllowed(context: Context): Boolean =
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getLong(KEY_ALLOWED_UNTIL, 0L) > System.currentTimeMillis()

        private fun allowForLaunch(context: Context) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putLong(KEY_ALLOWED_UNTIL, System.currentTimeMillis() + LAUNCH_GRACE_MILLIS)
                .apply()
        }
    }
}
