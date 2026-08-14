package com.atsuya.dailydashboard

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityEvent

/** クラス名は既存のユーザー補助許可を引き継ぐため維持している。 */
class YouTubeGateAccessibilityService : AccessibilityService() {
    private var lastForegroundPackage = ""

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val packageName = event.packageName?.toString().orEmpty()
        val enteredInstagram = packageName == INSTAGRAM_PACKAGE &&
            lastForegroundPackage != INSTAGRAM_PACKAGE
        lastForegroundPackage = packageName

        if (!enteredInstagram || YouTubeGateActivity.isTemporarilyAllowed(this)) return
        startActivity(
            Intent(this, YouTubeGateActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        )
    }

    override fun onInterrupt() = Unit

    companion object {
        const val INSTAGRAM_PACKAGE = "com.instagram.android"
    }
}
