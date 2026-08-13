package com.atsuya.dailydashboard

import android.app.Activity
import android.os.Bundle
import android.widget.Toast

/** Pixelの背面クイックタップなどから、画面を開かず集中を開始する入口。 */
class QuickStartActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        when (TimerScheduler.status(this)) {
            "running" -> Toast.makeText(this, "集中タイマーは実行中です", Toast.LENGTH_SHORT).show()
            "paused" -> {
                TimerScheduler.resume(this)
                Toast.makeText(this, "集中タイマーを再開しました", Toast.LENGTH_SHORT).show()
            }
            else -> {
                TimerScheduler.schedulePreset(this, "work")
                Toast.makeText(
                    this,
                    "${TimerScheduler.preferredFocus(this)}・25分を開始しました",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
        finish()
        overridePendingTransition(0, 0)
    }
}
