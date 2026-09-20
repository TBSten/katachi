# sample/android

katachi のサンプルのうち、**マルチモジュール構成の Android アプリ**。
`sample/jvm` `sample/kmp` と同じく、`settings.gradle.kts` と gradle wrapper を自前で持つ**独立した Gradle ビルド**で、
`includeBuild("../..")` によって katachi をリポジトリのソースから取り込む。

利用者と同じ書き方（`testImplementation(libs.katachi)`）で katachi を使い、
実際のプロジェクト構成に対して DSL が機能するかを確かめる結合テストの場。

## Android SDK の場所

このサンプルのビルドには Android SDK が要る。どちらか一方を用意する。

```sh
# A. 環境変数で渡す
ANDROID_HOME=$HOME/Library/Android/sdk ./gradlew :app:testDebugUnitTest

# B. local.properties を作る（.gitignore 済み。コミットしないこと）
echo "sdk.dir=$HOME/Library/Android/sdk" > local.properties
```

どちらも無いと `SDK location not found. Define a valid SDK location with an
ANDROID_HOME environment variable or by setting the sdk.dir path in your project's
local properties file` で失敗する。

GitHub Actions の ubuntu / macOS ランナーは `ANDROID_HOME` が設定済みなので、CI では A が自動的に効く。

## 実行

```sh
./gradlew :app:testDebugUnitTest   # katachi のテスト（kotest FreeSpec）
./gradlew assembleDebug            # 9 モジュールのビルドと APK 生成
```

`check` は 9 モジュール分の Android Lint を含んで 480 タスク超になるので、普段の確認には上の 2 つを使う。

## モジュール構成

| モジュール | 中身 |
|---|---|
| `:app` | `MainActivity` / `MainApplication` / `AndroidManifest.xml` / `res/` / `proguard-rules.pro`、および katachi のテスト |
| `:feature:home` `:feature:settings` | `<Feature>Screen.kt` / `<Feature>ViewModel.kt` / `<Feature>Route.kt` |
| `:data` | `*Repository.kt` / `*RepositoryImpl.kt` |
| `:ui:component` `:ui:theme` `:ui:core` | UI の共通部品 |
| `:navigation` | 画面遷移 |
| `:testing` | 他モジュールのテストから使う Fake |

中身はファイル配置を検査するためのスタブ。**Compose には依存していない**
（AGP / Compose / Kotlin のバージョン組み合わせで詰まるリスクを避けるため。
konsist でクラスの中身を検査したくなった時点で必要な分だけ実物に近づける）。

## katachi の定義

- `app/src/test/kotlin/com/example/sample/ProjectArchitecture.kt` — group と役割の定義
- `app/src/test/kotlin/com/example/sample/ProjectArchitectureSpec.kt` — 組み上がった `Architecture` の検証
- `app/src/test/kotlin/com/example/sample/ProjectRootSpec.kt` — プロジェクトルート特定の前提条件を守る番兵

`layout { }` の中身は実装ステップ2以降で書き足す。
