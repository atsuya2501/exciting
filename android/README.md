# デイリーダッシュボード Android版

既存のWebアプリをAndroid WebViewへ内蔵し、端末機能をKotlinで補強した個人用Androidアプリです。

## Android側の機能

- アプリを閉じてもポモドーロ終了を通知
- 端末再起動後に未完了のタイマーを再登録
- Capture Inbox用MarkdownをAndroidの共有画面へ渡す
- JSONバックアップの保存先選択・復元ファイル選択
- Webデータを端末内に保存し、オフラインで動作
- 外部リンクは通常のブラウザ／対応アプリで開く

## Android Studioで開く

1. ZIPを `C:\AndroidProjects\Pomodoro` など半角英数字だけの場所へ展開
2. Android Studioでこのフォルダを `Open`
3. Gradle Syncの完了を待つ
4. Android 10以上の実機またはエミュレーターを選択
5. 上部の実行ボタンを押す

WindowsではAndroidのビルド機能が日本語を含むパスを拒否する場合があります。回避設定も同梱していますが、OneDriveや日本語フォルダの外に置くほうが、同期によるファイルロックも避けられて安定します。

初回起動時に通知許可が表示されます。ポモドーロ終了通知を使う場合は許可してください。

## APKを作る

Android Studioのメニューから `Build > Build App Bundle(s) / APK(s) > Build APK(s)` を選びます。

コマンドラインでは次のとおりです。

```powershell
.\gradlew.bat assembleDebug
```

生成先:

```text
app/build/outputs/apk/debug/app-debug.apk
```

## Web画面の更新

Web版のファイルは `app/src/main/assets/web/` に入っています。Web版を変更したときは、このフォルダのファイルも更新してください。

ポモドーロ、共有、バックアップは `window.Android` 経由でKotlin側へ接続しています。
