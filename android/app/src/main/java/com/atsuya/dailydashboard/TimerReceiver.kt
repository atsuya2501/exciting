package com.atsuya.dailydashboard

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.os.Build
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

class TimerReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val completion = TimerScheduler.complete(context)
        val message = completion.message
        wakeScreen(context)
        vibrateAsAlarm(context)
        TimerScheduler.scheduleAlertAutoStop(context)
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = context.getString(R.string.notification_channel_description)
                enableVibration(true)
                vibrationPattern = VIBRATION_PATTERN
                setSound(null, ALARM_ATTRIBUTES)
                lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
            }
        )

        if (
            ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) return

        val openIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_timer)
            .setContentTitle(message)
            .setContentText(completion.focus.ifBlank { context.getString(R.string.app_name) })
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setVibrate(VIBRATION_PATTERN)
            .setAutoCancel(true)
            .setContentIntent(openIntent)
            .addAction(
                R.drawable.ic_timer,
                nextActionLabel(completion.nextMode),
                TimerScheduler.nextActionPendingIntent(context, completion.nextMode)
            )
            .addAction(
                R.drawable.ic_timer,
                "振動を停止",
                TimerScheduler.stopAlertPendingIntent(context)
            )
            .build()

        NotificationManagerCompat.from(context).notify(TimerScheduler.NOTIFICATION_ID, notification)
    }

    @Suppress("DEPRECATION")
    private fun wakeScreen(context: Context) {
        val powerManager = context.getSystemService(PowerManager::class.java)
        val wakeLock = powerManager.newWakeLock(
            PowerManager.SCREEN_BRIGHT_WAKE_LOCK or
                PowerManager.ACQUIRE_CAUSES_WAKEUP or
                PowerManager.ON_AFTER_RELEASE,
            "DailyDashboard:PomodoroFinished"
        )
        // ロックは解除せず、終了に気づける時間だけ画面を点灯する。
        wakeLock.acquire(5_000L)
    }

    @Suppress("DEPRECATION")
    private fun vibrateAsAlarm(context: Context) {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(VibratorManager::class.java).defaultVibrator
        } else {
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
        if (vibrator.hasVibrator()) {
            // Pixelのサイレント／バイブモードでも終了合図として扱われるよう、
            // 通常通知ではなくアラーム用途を明示する。
            vibrator.vibrate(
                VibrationEffect.createWaveform(REPEATING_VIBRATION_PATTERN, 0),
                ALARM_ATTRIBUTES
            )
        }
    }

    private fun nextActionLabel(mode: String): String = when (mode) {
        "short" -> "5分休憩を開始"
        "long" -> "15分休憩を開始"
        else -> "次の25分を開始"
    }

    companion object {
        // 旧版で作られた通知チャンネル設定を確実に更新するためIDを変更。
        private const val CHANNEL_ID = "pomodoro_alerts_v3"
        private val VIBRATION_PATTERN = longArrayOf(0, 450, 180, 450, 180, 750)
        private val REPEATING_VIBRATION_PATTERN = longArrayOf(0, 700, 1_300)
        private val ALARM_ATTRIBUTES = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
    }
}
