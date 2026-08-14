package com.atsuya.dailydashboard

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityEvent

class YouTubeGateAccessibilityService : AccessibilityService() {
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val target = when (event.packageName?.toString()) {
            YOUTUBE_PACKAGE -> TARGET_YOUTUBE
            INSTAGRAM_PACKAGE -> TARGET_INSTAGRAM
            else -> return
        }
        if (TimerScheduler.status(this) == "running") return
        if (YouTubeGateActivity.isTemporarilyAllowed(this, target)) return

        startActivity(
            Intent(this, YouTubeGateActivity::class.java)
                .putExtra(YouTubeGateActivity.EXTRA_TARGET, target)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        )
    }

    override fun onInterrupt() = Unit

    companion object {
        const val YOUTUBE_PACKAGE = "com.google.android.youtube"
        const val INSTAGRAM_PACKAGE = "com.instagram.android"
        const val TARGET_YOUTUBE = "youtube"
        const val TARGET_INSTAGRAM = "instagram"
    }
}
