package com.atsuya.dailydashboard

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlarmManager
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.FileProvider
import java.io.File

class MainActivity : Activity() {
    private lateinit var webView: WebView
    private var pendingBackup: String? = null

    @SuppressLint("SetJavaScriptEnabled", "JavascriptInterface")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        webView = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.allowFileAccess = true
            settings.allowContentAccess = true
            settings.mediaPlaybackRequiresUserGesture = false
            addJavascriptInterface(NativeBridge(), "Android")
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(
                    view: WebView,
                    request: WebResourceRequest
                ): Boolean {
                    val uri = request.url
                    if (uri.scheme == "file") return false
                    return openExternal(uri)
                }
            }
            loadUrl(WEB_APP_URL)
        }
        setContentView(webView)
        requestNotificationPermission()
        requestExactAlarmPermission()
        scheduleDebugTimerIfRequested(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        TimerScheduler.stopAlert(this, false)
        scheduleDebugTimerIfRequested(intent)
    }

    private fun scheduleDebugTimerIfRequested(intent: Intent?) {
        if (intent?.getBooleanExtra(CLEAR_MATH_EXTRA, false) == true && ::webView.isInitialized) {
            webView.evaluateJavascript("window.clearMathPomodoros?.()", null)
            Toast.makeText(this, "数学のポモ記録を削除しました", Toast.LENGTH_SHORT).show()
            intent.removeExtra(CLEAR_MATH_EXTRA)
        }
        if (intent?.getBooleanExtra(DEBUG_TIMER_EXTRA, false) != true) return
        TimerScheduler.schedule(
            this,
            System.currentTimeMillis() + 10_000L,
            "work",
            "short",
            "10秒テスト完了",
            "振動テスト"
        )
        Toast.makeText(this, "10秒後に振動します", Toast.LENGTH_SHORT).show()
        intent.removeExtra(DEBUG_TIMER_EXTRA)
    }

    private fun requestNotificationPermission() {
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ActivityCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                NOTIFICATION_PERMISSION_REQUEST
            )
        }
    }

    private fun requestExactAlarmPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        val alarmManager = getSystemService(AlarmManager::class.java)
        if (alarmManager.canScheduleExactAlarms()) return
        runCatching {
            startActivity(
                Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                    data = Uri.parse("package:$packageName")
                }
            )
        }.onFailure {
            Toast.makeText(this, "正確なタイマーの許可を有効にしてください", Toast.LENGTH_LONG).show()
        }
    }

    private fun openExternal(uri: Uri): Boolean {
        return try {
            startActivity(Intent(Intent.ACTION_VIEW, uri))
            true
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(this, "このリンクを開けるアプリがありません", Toast.LENGTH_SHORT).show()
            true
        }
    }

    override fun onBackPressed() {
        if (webView.canGoBack()) webView.goBack() else super.onBackPressed()
    }

    override fun onResume() {
        super.onResume()
        if (TimerScheduler.isFinished(this)) TimerScheduler.stopAlert(this, false)
        if (::webView.isInitialized) webView.evaluateJavascript("window.syncNativeTimer?.()", null)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != RESULT_OK) {
            if (requestCode == REQUEST_CREATE_BACKUP) pendingBackup = null
            return
        }

        when (requestCode) {
            REQUEST_CREATE_BACKUP -> {
                val content = pendingBackup
                val uri = data?.data
                pendingBackup = null
                if (content == null || uri == null) return
                runCatching {
                    contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(content) }
                        ?: error("出力先を開けません")
                }.onSuccess {
                    Toast.makeText(this, "バックアップを保存しました", Toast.LENGTH_SHORT).show()
                }.onFailure {
                    Toast.makeText(this, "バックアップを保存できませんでした", Toast.LENGTH_LONG).show()
                }
            }

            REQUEST_OPEN_BACKUP -> {
                val uri = data?.data ?: return
                runCatching {
                    contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                        ?: error("ファイルを開けません")
                }.onSuccess { json ->
                    val quoted = org.json.JSONObject.quote(json)
                    webView.evaluateJavascript("window.importBackupFromAndroid($quoted)", null)
                }.onFailure {
                    Toast.makeText(this, "バックアップを読み込めませんでした", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    inner class NativeBridge {
        @JavascriptInterface
        fun scheduleTimer(endsAt: Long, mode: String, nextMode: String, message: String, focus: String) {
            TimerScheduler.schedule(this@MainActivity, endsAt, mode, nextMode, message, focus)
        }

        @JavascriptInterface
        fun cancelTimer() {
            TimerScheduler.cancel(this@MainActivity)
        }

        @JavascriptInterface
        fun finishTimerNow() {
            TimerScheduler.finishNow(this@MainActivity)
        }

        @JavascriptInterface
        fun getTimerState(): String = TimerScheduler.currentStateJson(this@MainActivity)

        @JavascriptInterface
        fun setPreferredFocus(focus: String) {
            TimerScheduler.setPreferredFocus(this@MainActivity, focus)
        }

        @JavascriptInterface
        fun shareMarkdown(filename: String, content: String) {
            runOnUiThread {
                runCatching {
                    val safeName = filename.replace(Regex("[^A-Za-z0-9._-]"), "_").take(100)
                    val directory = File(cacheDir, "shared").apply { mkdirs() }
                    val file = File(directory, safeName).apply { writeText(content, Charsets.UTF_8) }
                    val uri = FileProvider.getUriForFile(
                        this@MainActivity,
                        "$packageName.files",
                        file
                    )
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/markdown"
                        putExtra(Intent.EXTRA_STREAM, uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    startActivity(Intent.createChooser(intent, "Capture Inboxへ送る"))
                }.onFailure {
                    Toast.makeText(this@MainActivity, "メモを共有できませんでした", Toast.LENGTH_LONG).show()
                }
            }
        }

        @JavascriptInterface
        fun saveBackup(filename: String, content: String) {
            runOnUiThread {
                pendingBackup = content
                val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "application/json"
                    putExtra(Intent.EXTRA_TITLE, filename)
                }
                startActivityForResult(intent, REQUEST_CREATE_BACKUP)
            }
        }

        @JavascriptInterface
        fun openBackup() {
            runOnUiThread {
                val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "application/json"
                }
                startActivityForResult(intent, REQUEST_OPEN_BACKUP)
            }
        }

        @JavascriptInterface
        fun openNotificationSettings() {
            runOnUiThread {
                val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                    putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
                }
                startActivity(intent)
            }
        }
    }

    companion object {
        private const val WEB_APP_URL = "file:///android_asset/web/index.html"
        private const val REQUEST_CREATE_BACKUP = 2001
        private const val REQUEST_OPEN_BACKUP = 2002
        private const val NOTIFICATION_PERMISSION_REQUEST = 2003
        private const val DEBUG_TIMER_EXTRA = "debug_timer_10s"
        private const val CLEAR_MATH_EXTRA = "clear_math_pomodoros"
    }
}
