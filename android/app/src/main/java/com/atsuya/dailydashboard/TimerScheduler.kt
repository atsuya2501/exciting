package com.atsuya.dailydashboard

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import org.json.JSONObject

object TimerScheduler {
    private const val PREFS = "pomodoro_alarm"
    private const val KEY_ENDS_AT = "ends_at"
    private const val KEY_MODE = "mode"
    private const val KEY_NEXT_MODE = "next_mode"
    private const val KEY_MESSAGE = "message"
    private const val KEY_ACTIVE = "active"
    private const val KEY_STATUS = "status"
    private const val KEY_REMAINING = "remaining"
    private const val KEY_FOCUS = "focus"
    private const val KEY_COMPLETION_ID = "completion_id"
    private const val KEY_COMPLETED_MODE = "completed_mode"
    private const val KEY_COMPLETED_FOCUS = "completed_focus"
    private const val REQUEST_CODE = 4101
    // Pixelが低優先度通知をロック画面から省略するため、表示優先度だけ通常にする。
    // 旧チャンネルの重要度は後から変更できないので新しいIDを使用。
    private const val RUNNING_CHANNEL_ID = "pomodoro_running_visible_v2"
    const val NOTIFICATION_ID = 4101

    data class Completion(val message: String, val nextMode: String, val focus: String)

    fun schedule(
        context: Context,
        endsAt: Long,
        mode: String,
        nextMode: String,
        message: String,
        focus: String = ""
    ) {
        if (endsAt <= System.currentTimeMillis()) return
        stopAlert(context, false)
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putLong(KEY_ENDS_AT, endsAt)
            .putString(KEY_MODE, sanitizeMode(mode))
            .putString(KEY_NEXT_MODE, sanitizeMode(nextMode))
            .putString(KEY_MESSAGE, message)
            .putString(KEY_FOCUS, focus.trim().take(100))
            .putBoolean(KEY_ACTIVE, true)
            .putString(KEY_STATUS, "running")
            .remove(KEY_REMAINING)
            .apply()

        context.getSystemService(AlarmManager::class.java).setAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            endsAt,
            alarmPendingIntent(context)
        )
        showRunningNotification(context, endsAt, sanitizeMode(mode), focus)
    }

    fun schedulePreset(context: Context, mode: String) {
        val safeMode = sanitizeMode(mode)
        val seconds = when (safeMode) {
            "short" -> 5 * 60
            "long" -> 15 * 60
            else -> 25 * 60
        }
        val next = if (safeMode == "work") "short" else "work"
        val message = if (safeMode == "work") {
            "作業終了!休憩しましょう 🍅"
        } else {
            "休憩終了!次の作業を始めましょう 💪"
        }
        val focus = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_FOCUS, "") ?: ""
        schedule(context, System.currentTimeMillis() + seconds * 1000L, safeMode, next, message, focus)
    }

    fun cancel(context: Context) {
        stopAlert(context, false)
        context.getSystemService(AlarmManager::class.java).cancel(alarmPendingIntent(context))
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().clear().putString(KEY_STATUS, "idle").apply()
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
    }

    fun pause(context: Context) {
        stopAlert(context, false)
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val remaining = (prefs.getLong(KEY_ENDS_AT, 0L) - System.currentTimeMillis()).coerceAtLeast(0L)
        context.getSystemService(AlarmManager::class.java).cancel(alarmPendingIntent(context))
        prefs.edit()
            .putBoolean(KEY_ACTIVE, false)
            .putString(KEY_STATUS, "paused")
            .putLong(KEY_REMAINING, remaining)
            .apply()
        showPausedNotification(context, remaining)
    }

    fun resume(context: Context) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val remaining = prefs.getLong(KEY_REMAINING, 0L)
        if (remaining <= 0L) { cancel(context); return }
        schedule(
            context,
            System.currentTimeMillis() + remaining,
            prefs.getString(KEY_MODE, "work") ?: "work",
            prefs.getString(KEY_NEXT_MODE, "short") ?: "short",
            prefs.getString(KEY_MESSAGE, "タイマーが終了しました") ?: "タイマーが終了しました",
            prefs.getString(KEY_FOCUS, "") ?: ""
        )
    }

    fun restore(context: Context) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val endsAt = prefs.getLong(KEY_ENDS_AT, 0L)
        if (!prefs.getBoolean(KEY_ACTIVE, false) || endsAt <= System.currentTimeMillis()) return
        schedule(
            context,
            endsAt,
            prefs.getString(KEY_MODE, "work") ?: "work",
            prefs.getString(KEY_NEXT_MODE, "short") ?: "short",
            prefs.getString(KEY_MESSAGE, "タイマーが終了しました") ?: "タイマーが終了しました",
            prefs.getString(KEY_FOCUS, "") ?: ""
        )
    }

    fun complete(context: Context): Completion {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val completion = Completion(
            prefs.getString(KEY_MESSAGE, "タイマーが終了しました") ?: "タイマーが終了しました",
            sanitizeMode(prefs.getString(KEY_NEXT_MODE, "work") ?: "work"),
            prefs.getString(KEY_FOCUS, "") ?: ""
        )
        prefs.edit().putBoolean(KEY_ACTIVE, false).putString(KEY_STATUS, "finished")
            .putLong(KEY_COMPLETION_ID, System.currentTimeMillis())
            .putString(KEY_COMPLETED_MODE, prefs.getString(KEY_MODE, "work"))
            .putString(KEY_COMPLETED_FOCUS, prefs.getString(KEY_FOCUS, ""))
            .apply()
        return completion
    }

    fun finishNow(context: Context) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getString(KEY_STATUS, "idle") == "finished") return
        context.getSystemService(AlarmManager::class.java).cancel(alarmPendingIntent(context))
        TimerReceiver().onReceive(context, Intent(context, TimerReceiver::class.java))
    }

    fun currentStateJson(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val endsAt = prefs.getLong(KEY_ENDS_AT, 0L)
        val active = prefs.getBoolean(KEY_ACTIVE, false) && endsAt > System.currentTimeMillis()
        val status = if (active) "running" else prefs.getString(KEY_STATUS, "idle")
        return JSONObject()
            .put("active", active)
            .put("status", status)
            .put("endsAt", endsAt)
            .put("mode", prefs.getString(KEY_MODE, "work"))
            .put("remainingMs", prefs.getLong(KEY_REMAINING, 0L))
            .put("focus", prefs.getString(KEY_FOCUS, ""))
            .put("completionId", prefs.getLong(KEY_COMPLETION_ID, 0L))
            .put("completedMode", prefs.getString(KEY_COMPLETED_MODE, ""))
            .put("completedFocus", prefs.getString(KEY_COMPLETED_FOCUS, ""))
            .toString()
    }

    fun isFinished(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_STATUS, "idle") == "finished"

    private fun showRunningNotification(context: Context, endsAt: Long, mode: String, focus: String) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                RUNNING_CHANNEL_ID,
                "実行中のポモドーロ",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "ロック画面に残り時間を表示します"
                enableVibration(false)
                setSound(null, null)
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
        val title = when (mode) {
            "short" -> "休憩中"
            "long" -> "長休憩中"
            else -> "集中中"
        }
        val notification = NotificationCompat.Builder(context, RUNNING_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_timer)
            .setContentTitle(title)
            .setContentText(focus.ifBlank { "残り時間" })
            .setWhen(endsAt)
            .setUsesChronometer(true)
            .setChronometerCountDown(true)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(openIntent)
            .addAction(
                R.drawable.ic_timer,
                "一時停止",
                controlPendingIntent(context, TimerActionReceiver.ACTION_PAUSE, 4201)
            )
            .addAction(
                R.drawable.ic_timer,
                "リセット",
                controlPendingIntent(context, TimerActionReceiver.ACTION_RESET, 4202)
            )
            .build()
        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
    }

    private fun showPausedNotification(context: Context, remainingMs: Long) {
        if (
            ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) return
        val seconds = remainingMs / 1000
        val text = "残り %02d:%02d".format(seconds / 60, seconds % 60)
        val notification = NotificationCompat.Builder(context, RUNNING_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_timer)
            .setContentTitle("一時停止中 $text")
            .setContentText(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(KEY_FOCUS, "")?.ifBlank { "集中内容なし" })
            .setOngoing(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .addAction(
                R.drawable.ic_timer,
                "再開",
                controlPendingIntent(context, TimerActionReceiver.ACTION_RESUME, 4203)
            )
            .addAction(
                R.drawable.ic_timer,
                "リセット",
                controlPendingIntent(context, TimerActionReceiver.ACTION_RESET, 4202)
            )
            .build()
        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
    }

    fun nextActionPendingIntent(context: Context, nextMode: String): PendingIntent {
        val intent = Intent(context, TimerActionReceiver::class.java)
            .setAction(TimerActionReceiver.ACTION_NEXT)
            .putExtra(TimerActionReceiver.EXTRA_MODE, sanitizeMode(nextMode))
        return PendingIntent.getBroadcast(
            context,
            4102,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    fun scheduleAlertAutoStop(context: Context) {
        context.getSystemService(AlarmManager::class.java).setAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            System.currentTimeMillis() + ALERT_MAX_DURATION_MS,
            stopAlertPendingIntent(context)
        )
    }

    fun stopAlert(context: Context, dismissNotification: Boolean = true) {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val manager = context.getSystemService(VibratorManager::class.java)
            manager.cancel()
            manager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
        vibrator.cancel()
        context.getSystemService(AlarmManager::class.java).cancel(stopAlertPendingIntent(context))
        if (dismissNotification) NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
    }

    fun stopAlertPendingIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context,
        4204,
        Intent(context, StopAlertReceiver::class.java).setAction("com.atsuya.dailydashboard.STOP_ALERT_NOW"),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    private fun controlPendingIntent(context: Context, action: String, requestCode: Int): PendingIntent {
        return PendingIntent.getBroadcast(
            context,
            requestCode,
            Intent(context, TimerActionReceiver::class.java).setAction(action),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun alarmPendingIntent(context: Context): PendingIntent {
        return PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            Intent(context, TimerReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun sanitizeMode(mode: String): String =
        if (mode in setOf("work", "short", "long")) mode else "work"

    private const val ALERT_MAX_DURATION_MS = 3 * 60 * 1000L
}
