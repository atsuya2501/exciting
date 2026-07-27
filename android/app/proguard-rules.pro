# JavaScriptから呼び出すメソッド名を保持する。
-keepclassmembers class com.atsuya.dailydashboard.MainActivity$NativeBridge {
    @android.webkit.JavascriptInterface <methods>;
}
