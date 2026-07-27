package com.atsuya.dailydashboard

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class StopAlertReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        TimerScheduler.stopAlert(context)
    }
}
