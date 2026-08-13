package com.atsuya.dailydashboard

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityEvent

class YouTubeGateAccessibilityService : AccessibilityService() {
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        if (event.packageName?.toString() != YOUTUBE_PACKAGE) return
        if (TimerScheduler.status(this) == "running") return
        if (YouTubeGateActivity.isTemporarilyAllowed(this)) return

        startActivity(
            Intent(this, YouTubeGateActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        )
    }

    override fun onInterrupt() = Unit

    companion object {
        const val YOUTUBE_PACKAGE = "com.google.android.youtube"
    }
}
