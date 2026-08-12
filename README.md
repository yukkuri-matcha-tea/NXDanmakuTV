# NX弾幕TV

Android TV / Google TVの地デジ画面上へ、NX-Jikkyoのリアルタイムコメントを
透明オーバーレイ表示する非公式アプリです。

## 主な機能

- Accessibility Serviceによる地デジチャンネルの自動追従
- NX-Jikkyo視聴・コメントWebSocketへの接続と自動復旧
- 通常、上固定、下固定、色、サイズなどのコメント表示
- ホーム、設定、入力切替などテレビ以外の画面で自動非表示
- 接続・切断・再接続・チャンネル変更を示す左上イベントログ
- イベントログと接続状態バッジの個別ON/OFF
- 文字サイズ、速度、透明度、密度、表示範囲、縁取り、遅延の調整
- 表示設定の初期値リセット
- TVリモコン向けのカード型設定画面
- GitHub Releasesを利用した起動時の自動更新確認
- 更新APKのダウンロード、検証、標準インストーラーへの受け渡し

対応実況チャンネルは `jk1`, `jk2`, `jk4`, `jk5`, `jk6`, `jk7`, `jk8`,
`jk9`, `jk10` です。

## インストール

1. GitHub Releasesから `NXDanmakuTV-1.0.0.apk` をAndroid TVへ転送します。
2. TV側で「不明なアプリのインストール」を一時的に許可してAPKを開きます。
3. NX弾幕TVを起動し、「オーバーレイ権限」を許可します。
4. 「自動追従権限」から「NX弾幕TV 自動追従」を有効にします。
5. 「地デジでコメント表示を開始」を押します。

起動時にGitHub Releasesを自動確認します。更新がある場合は画面の案内から
APKをダウンロードできます。Androidの仕様上、インストールの最終確認は必要です。

アプリのパッケージ名は `com.yukkurimatchatea.nxdanmakutv` です。過去のテスト版を
導入している場合は、アンインストールしてから正式版を導入してください。

## 権限について

Accessibility Serviceは、純正TVアプリの局名・局番号OSD、アクティブな画面、
リモコンのチャンネル操作を端末内で参照します。入力は消費せず純正TVアプリへ
そのまま渡します。取得したAccessibility情報を開発者のサーバーへ送信しません。

詳細は [PRIVACY.md](PRIVACY.md) を参照してください。

最新版: [GitHub Releases](https://github.com/yukkuri-matcha-tea/NXDanmakuTV/releases/latest)

## 実機上の制約

メーカー純正TVアプリがAccessibilityへ局名を公開せず、リモコンキーも外部サービスへ
渡さない機種では、自動追従できない場合があります。また、純正TVアプリがシステム
以外のオーバーレイを禁止する機種ではコメントを表示できません。

「チャンネル診断」の手動ボタンでNX-Jikkyo接続と描画部分を切り分けられます。

## 開発ビルド

Android StudioのJBRとAndroid SDK 35を使用します。

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
$env:ANDROID_HOME="$env:LOCALAPPDATA\Android\Sdk"
.\gradlew.bat testDebugUnitTest assembleDebug lintDebug
```

## 配布用ビルド

`keystore.properties.example` を `keystore.properties` へコピーし、公開しない署名鍵の
パスとパスワードを指定します。

```powershell
.\gradlew.bat clean testDebugUnitTest lintRelease assembleRelease
```

出力は `app/build/outputs/apk/release/app-release.apk` です。公開前に必ず
`apksigner verify --verbose --print-certs` とSHA-256を確認してください。

## 注意

NX弾幕TVはNX-Jikkyo、ニコニコ、各テレビ局およびテレビメーカーの公式アプリでは
ありません。サービス側の仕様変更により動作しなくなる可能性があります。
