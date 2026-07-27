package com.atsuya.dailydashboard

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class TimerActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_PAUSE -> TimerScheduler.pause(context)
            ACTION_RESUME -> { TimerScheduler.stopAlert(context, false); TimerScheduler.resume(context) }
            ACTION_RESET -> TimerScheduler.cancel(context)
            ACTION_NEXT -> { TimerScheduler.stopAlert(context, false); TimerScheduler.schedulePreset(context, intent.getStringExtra(EXTRA_MODE) ?: "work") }
            ACTION_STOP_ALERT -> TimerScheduler.stopAlert(context)
        }
    }

    companion object {
        const val EXTRA_MODE = "mode"
        const val ACTION_PAUSE = "com.atsuya.dailydashboard.PAUSE"
        const val ACTION_RESUME = "com.atsuya.dailydashboard.RESUME"
        const val ACTION_RESET = "com.atsuya.dailydashboard.RESET"
        const val ACTION_NEXT = "com.atsuya.dailydashboard.NEXT"
        const val ACTION_STOP_ALERT = "com.atsuya.dailydashboard.STOP_ALERT"
    }
}
