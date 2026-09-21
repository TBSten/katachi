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
./gradlew assembleDebug            # 7 モジュールのビルドと APK 生成
```

`check` は 7 モジュール分の Android Lint を含んで大量のタスクになるので、普段の確認には上の 2 つを使う。

## モジュール構成

| モジュール | 中身 |
|---|---|
| `:app` | `MainActivity`（`ComponentActivity` + `setContent` + `NavHost`）/ `MainApplication` / `AndroidManifest.xml` / `res/` / `proguard-rules.pro`、および katachi のテスト |
| `:feature:home` `:feature:settings` | `<Feature>Screen.kt`（`@Composable`）/ `<Feature>ViewModel.kt`（`androidx.lifecycle.ViewModel` + `StateFlow`）/ `<Feature>Route.kt`（`NavGraphBuilder` 拡張） |
| `:data` | `*Repository.kt` / `*RepositoryImpl.kt`。扱う対象ごとの package（`user` / `settings`）に分けて置く |
| `:ui` | UI 層すべて。`component` / `theme` / `core` / `preview` は**この 1 モジュール内の package**（以前の `:ui:component` `:ui:theme` `:ui:core` は廃止） |
| `:navigation` | `AppNavigator`。画面遷移の窓口を feature に渡す（グラフの組み立て自体は `:app`） |
| `:testing` | 他モジュールのテストから使う Fake |

Compose / AndroidX の実依存を入れてあり、`assembleDebug` は実際に Compose コンパイラを通る。
`@Preview` も `:ui` と `:feature:*` に置いてある。どれも `:ui` の `preview` package にある
`PreviewRoot { }` で中身を包む（テーマと背景を 1 箇所で決め、`darkTheme` で明暗を出し分ける）。

## バージョンの制約

- Kotlin / katachi / kotest はリポジトリルートの `gradle/libs.versions.toml`（`libs`）が SSoT。
  Compose コンパイラプラグイン（`org.jetbrains.kotlin.plugin.compose`）もそこの
  `libs.plugins.kotlinPluginCompose` を `alias` で参照して Kotlin バージョンに追従するので、
  バージョンはルートの build ファイル以外に書かない。
- AGP は **9.1.0 で固定**。これより新しいと Android Studio の Gradle sync が
  `The project is using an incompatible version` で止まる。
- AGP 9.1.0 が対応する `compileSdk` は 36 まで。AndroidX の AAR はそれぞれ
  `minCompileSdk` を宣言しており、それが 37 になったバージョンは使えない。
  `gradle/sample.versions.toml` の AndroidX のバージョンは「`minCompileSdk` が 36 以下で最新」を選んである。
  上げる前に確認する:

  ```sh
  unzip -p <artifact>.aar META-INF/com/android/build/gradle/aar-metadata.properties
  ```

## katachi の定義

- `app/src/test/kotlin/com/example/sample/ProjectArchitecture.kt` — group と役割の定義
- `app/src/test/kotlin/com/example/sample/ProjectArchitectureSpec.kt` — 組み上がった `Architecture` の検証
- `app/src/test/kotlin/com/example/sample/ProjectRootSpec.kt` — プロジェクトルート特定の前提条件を守る番兵

`layout { }` の中身は実装ステップ2以降で書き足す。
