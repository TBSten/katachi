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

`architecture-test/src/test/kotlin/com/example/sample/` に置いてある。

`:architecture-test` は素の `kotlin("jvm")` モジュールで、アプリのどのレイヤーにも属さない。
katachi の推奨導入形そのもので、3サンプルとも同じ形になっている。

- `ProjectArchitecture.kt` — `architecture { }` 本体。下の拡張関数を呼ぶだけ
- `ProjectArchitectureTest.kt` — **利用者が書くのはこれだけ。** `projectArchitecture.assert()` を
  呼ぶ JUnit のテスト1個。違反は1つの失敗メッセージに全件まとまって出る
- `ProjectArchitectureSpec.kt` — 組み上がった `Architecture` の検証と、**わざと役割を欠いた定義**で
  期待どおりの違反が出ることの確認。katachi リポジトリ固有の自己検証で、導入するプロジェクトには要らない
- `ProjectRootSpec.kt` — プロジェクトルート特定の前提条件を守る番兵

group と役割の定義は、関心ごとに package を分けて `ArchitectureScope` の拡張関数にしてある。
katachi が推奨する分割の形そのもので、サンプルがその実例を兼ねる。

- `application/` — アプリ本体（`UiRoles.kt` / `DataRoles.kt` / `AppRoles.kt`）
- `testing/` — テスト関連（`TestingRoles.kt`）
- `gradle/` — ビルド設定（`GradleRoles.kt`）。`.gitignore` が `build/` を無視するので
  package 名は `build` ではなく `gradle`
- `application/` には `FeatureRoles.kt` も含む（`:feature:*` の `Screen` / `ViewModel` / `Route`）
- `tool/` — そのほかのツール（`ToolRoles.kt`）。いまは `.gitignore` と `README.md`

拡張関数は **`inline` にしない**。宣言位置はスタックトレースから取るので、inline すると
呼び出し元ファイルの存在しない行を指すようになる。`ProjectArchitectureSpec` は各宣言の
`declaredAt` がそれを書いたファイル（`UiRoles.kt` など）を指すことを検証していて、
これが分割しても宣言位置が壊れないことの証明になっている。

### `layout { }` の書き方（実装ステップ2 の状態）

いまの `layout { }` は**ディレクトリとファイルだけ**で書いてある。モジュールも sourceSet も
package も、素のディレクトリとして全部書き下す。

```kotlin
// ui/Theme
"ui" / "src" / "main" / "kotlin" / "com" / "example" / "sample" / "ui" / "theme" / "AppTheme".ktFile()
```

冗長なのは意図的で、実装ステップ3 で同じ内容を `":ui".module { }` ・ `mainSourceSet` ・
`modulePackage` ・ `wildcards` に書き直したときに**検査結果が変わらないこと**が、
糖衣が正しく展開されている証拠になる。

ステップ2 で使っていない機能: `"...".module { }`、sourceSet、`modulePackage`、`wildcards`、
`konsist { }`、Warning。

#### 生成物をどう扱っているか

**どの役割にも書いていない。** 既定の `files = gitTracked()` が
`git ls-files --cached --others --exclude-standard` の結果だけを検査対象にするので、
`.gitignore` が無視するものは最初から検査に上がってこない。

このサンプルの**プロジェクトルートは `sample/android`** で、そこに `.git` は無い
（リポジトリの `.git` は2階層上）。それでも git のフィルタは効く。katachi は
「ルート直下に `.git` があるか」ではなく `git rev-parse --is-inside-work-tree` で判定し、
`git ls-files` をルートで実行すると**そのサブツリーのファイルがルートからの相対パスで**返るため。

- 各モジュールの `build/`、ルートの `build/` と `.kotlin/` — `.gitignore` 済み
- `local.properties` — `.gitignore` 済み
- `.gradle/` — `.gitignore` 済み。加えて katachi の固定除外（`.git` / `.gradle` / `.idea`）にも入る

逆に `files = wholeTree()` に切り替えると、これらが軒並み `Unexpected` として出る。
それがフィルタの効きの確認方法でもある。

### なぜ `:app` ではなく専用モジュールなのか

もともとは `:app` の `src/test` に間借りしていた。移した理由は3つ。

- **アーキテクチャ定義は app の一部ではない。** `:app` に置くと、どのレイヤーにも属さないものが
  一レイヤーの持ち物に見える
- **プロジェクトの種別によらず同じ形になる。** `sample/kmp` は JVM ターゲットを持たないので
  `commonTest` に置けず、`:app:android` に間借りするしかなかった。専用モジュールならその問題が消える
- **v0.3 の Gradle plugin が種別で分岐しなくなる。** `sourceSets["test"]` は Android では variant 単位、
  KMP では存在しない。`kotlin("jvm")` のモジュールなら常に存在する

移設にあたって変更が要らなかったもの: `ProjectRootSpec` の期待値（1階層上が `sample/android/gradlew`）、
`ProjectArchitectureSpec` がソースを読み戻す相対パス（モジュール相対）、CI が回すタスク（`check` のまま
`:architecture-test:test` を含む）。

変更が要ったのは1点だけ。ルートの `build.gradle.kts` に
`alias(libs.plugins.kotlinJvm) apply false` を足した。AGP がバージョン無しの
Kotlin Gradle Plugin を buildscript classpath に載せるため、モジュール側でバージョンを指定すると
`already on the classpath with an unknown version` で落ちる。

