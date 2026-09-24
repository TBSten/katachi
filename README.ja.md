# katachi

**Android / KMP プロジェクトのアーキテクチャを Kotlin DSL で書き、同じ定義から「テスト」と「ドキュメント」の両方を出す**ためのライブラリ。

[English](./README.md) | 日本語

v0.1 は **Deny by default のアーキテクチャテスト**として出している。
「どの役割のファイルをどこに置けるか」を1箇所に宣言し、宣言に載っていないファイル（`Unexpected`）と、
宣言されているのに実体が無いもの（`Missing`）をテストで検出する。
ドキュメント生成は v0.3 の予定。

**ドキュメント: https://tbsten.github.io/katachi/ja/**

> [!NOTE]
> メジャーバージョンが 0 のあいだは、リリースに破壊的変更が入ることがある。

## 導入

**プロジェクトの種別（JVM / Android / KMP）によらず、手順は同じ4ステップ。**

1. **JVM モジュールを1つ作る。** 名前は `:architecture-test` など。Android でも KMP でも
   素の `kotlin("jvm")` モジュールにする（katachi は JVM ライブラリなので）
2. `testImplementation(katachi)` を足す
3. `architecture { }` を書く
4. テストを1個書く

```kotlin
// settings.gradle.kts
include(":architecture-test")
```

```kotlin
// architecture-test/build.gradle.kts
plugins { kotlin("jvm") }

kotlin { jvmToolchain(17) }

tasks.test { useJUnitPlatform() }

dependencies {
    testImplementation("me.tbsten.katachi:katachi:0.1.1")
    // 任意。`konsist { }` を書くときだけ。
    testImplementation("me.tbsten.katachi:katachi-konsist:0.1.1")

    // JUnit Platform に実行エンジンと launcher を載せる。katachi は AssertionError を
    // 投げるだけでテストフレームワークに依存しないので、エンジンは利用者が選ぶ。
    testImplementation(platform("org.junit:junit-bom:5.13.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
```

**JDK 17 以降**と **Kotlin 2.2 以降**が必要。公開している artifact は `languageVersion` 2.2 でビルドしているので、2.2 のコンパイラでも metadata を読める。それより古いとすべてのシンボルが `Unresolved reference` になる。

> [!IMPORTANT]
> **Kotlin 2.4 未満では `-Xcontext-parameters` を足す。** DSL の入口（`module` / `mainSourceSet` /
> `ktFile` / `konsist` など）はすべて context parameters なので、無いと1つも書けない。
>
> ```kotlin
> kotlin {
>     jvmToolchain(17)
>     compilerOptions.freeCompilerArgs.add("-Xcontext-parameters")
> }
> ```
>
> **Kotlin 2.4 以降では付けない。** 言語機能として入っているため、付けると redundant の
> 警告が出て、`allWarningsAsErrors` のビルドが落ちる。

> [!IMPORTANT]
> **`junit-platform-launcher` を忘れると、テストは起動すらしない。**
> `junit-jupiter` の集約 artifact は api / params / engine を含むが launcher は含まず、
> Gradle 9 は自動で載せない。`Failed to load JUnit Platform` で落ちる。
>
> **エンジンを載せ忘れると、テストは「成功」するのではなく1度も実行されない。**
> `useJUnitPlatform()` だけでは `@Test` を拾う実装が classpath に無く、`BUILD SUCCESSFUL` に
> なるのにアーキテクチャ検査が空振りする。kotest で書く場合は `kotest-runner-junit5` が
> 自前のエンジンを持つのでこれで足りるが、**`kotest-runner-junit5` は
> `junit-jupiter-api` しか連れてこない**ので、素の `@Test` を混ぜるなら上の `junit-jupiter`
> （engine 込み）が別途要る。`build/test-results/**/*.xml` の `tests=` が 0 でないことで確かめられる。

```kotlin
// architecture-test/src/test/kotlin/com/example/ProjectArchitectureTest.kt
class ProjectArchitectureTest {
    @Test fun `構成が allow list に従っている`() = projectArchitecture.assert()
}
```

アーキテクチャ定義はプロジェクト全体を記述するもので、**どのレイヤーにも属さない**。
だから既存モジュール（ルートの test や `:app` の test）に間借りさせず、モジュールを1つ立てる。
KMP プロジェクトではそもそも間借り先が無い（katachi は JVM only なので `commonTest` には置けない）。

代償はこのモジュール自身も allow list に載ることだが、
「役割を持たないファイルは存在しない」という katachi の原則からすればむしろ載るべきもの。

## 書き味

```kotlin
// :architecture-test の test sourceSet に置く
val projectArchitecture = architecture {
    "domain".group {
        title = "ドメイン"
        "UseCase" {
            title = "ユースケース"
            summary = "各画面で発生するアプリ固有の1つの振る舞い"
            example("GetUserUseCase", "ユーザーを取得する")
            layout {
                "domain" / "src" / "main" / "kotlin" / "com" / "example" / "useCase" / "*UseCase".ktFile()
            }
        }
    }
}
```

- Gradle plugin は要らない。`testImplementation` を足すだけ
- JUnit4 / JUnit5 / kotest のどれでも使える（将来の `assert()` は `AssertionError` を投げるだけ）
- 定義が大きくなったら `ArchitectureScope` の拡張関数に切り出してファイル分割できる。
  違反メッセージが示す宣言位置は、呼び出し元ではなく**その宣言を書いたファイル**を指す
  （3サンプルがこの形で書かれていて、テストで実証している）
  （分割に使う関数を `inline` にしないこと。`inline` にすると、宣言位置が
  呼び出し元ファイルの末尾より後ろの、存在しない行を指す）

## layout の書き方

`layout { }` の直下はリポジトリルート。文字列にブロックを付けるとディレクトリ、
`.file()` / `.ktFile()`（`.kt` を付ける）/ `.ktsFile()`（`.kts` を付ける）を付けるとファイルになる。
入れ子ブロックと `/` 連結は同じ意味で、キーに `"src/main/kotlin"` のような多階層を書いてもよい。

```kotlin
layout {
    ".gitignore".file()                  // リポジトリルート直下のファイル
    "app" {                              // ディレクトリ
        description = "アプリの入口"      // 同じ package に複数の役割が並ぶときの使い分け
        "src/main/kotlin/com/example" {
            "MainActivity".ktFile()      // MainActivity.kt
            "*ViewModel".ktFile()        // ワイルドカード
        }
        "res" { ignore() }               // 配下を何階層下でも検査しない
        "generated" { anyFile() }        // 直下の任意のファイルを許可（サブディレクトリは不可）
    }
    "build".ignore()                     // "build" { ignore() } と同じ
}
```

- **空のディレクトリブロックは「何も置けない」**。`"di" { }` の配下にファイルがあれば `Unexpected`
- **宣言したのに実体が無いファイルは `Missing`**。`.optional()` を付けると消える
- **ワイルドカードを含む宣言は自動で optional**。0件マッチでも `Missing` にならない
- `anyFile()` は**直下だけ**。サブディレクトリの中のファイルは `Unexpected` のまま
- `ignore()` は `layout { }` の中にしか無い。「検査しない」と決めた理由が役割の `summary` として
  ドキュメントに残るようにするため（グローバルな除外設定は用意しない）

### glob

katachi の glob は **`*` と `**` の2つだけ**。`{a,b}` / `?` / `[abc]` は
ワイルドカードとして働かず、書くとエラーになる（リテラルとして書きたければ `\*` のように
バックスラッシュでエスケープする）。

| 書き方 | 意味 |
|---|---|
| `*` | ちょうど1階層。`:feature:*` は `:feature:home` にマッチし、`:feature` にも `:feature:home:impl` にもマッチしない |
| `**` | 0階層以上。`:feature:**` は `:feature` / `:feature:home` / `:feature:home:impl` のすべてにマッチする |
| ファイル名の中の `*` | 名前の一部にマッチし、**ディレクトリの境界を越えない**。`*UseCase.kt` は `GetUserUseCase.kt` にマッチし、サブディレクトリの中の同名ファイルにはマッチしない |

- `*` は**0文字にはマッチしない**（`*UseCase.kt` は `UseCase.kt` にマッチしない）
- 照合は**常に大文字小文字を区別する**
- ディレクトリのパスでもモジュールパス（`:feature:home`）でも `*` / `**` の意味は同じ
- **ファイルの位置に `**` だけを書かない。** `"src/test/kotlin/**".file()` は
  `src/test/kotlin` までしか「既知のディレクトリ」にならず、`src/test/kotlin/com` が
  `[UnexpectedDirectory]` になる。`"src/test/kotlin" / "**" / "*".ktFile()` と書く
- **パスの先頭に `**` を置かない。** `"**/build".ignore()` は `**`（= 任意のパス）自体を
  既知のディレクトリとして登録してしまい、`[UnexpectedDirectory]` が一切出なくなる

## 検査対象のファイル集合

既定では **git が「このプロジェクトのファイル」と答えたものだけ**を検査する
（`git ls-files --cached --others --exclude-standard` の結果。追跡中 + 未追跡だが無視されていないもの）。
`build/` や `.DS_Store`、`local.properties` に役割を与える必要はない。

```kotlin
architecture {
    files = gitTracked()      // 既定。書かなくてもこれ
    // files = wholeTree()    // git を見ず、ファイルツリーをそのまま走査する
}
```

- ライブラリ依存はゼロだが、**既定では `git` コマンドをプロジェクトルートで起動する**
  （`rev-parse --is-inside-work-tree` で可否を判定し、`ls-files` を1回）
- プロジェクトルートは Konsist と同じ方式で特定する。作業ディレクトリから上へ辿り、
  `gradlew` / `mvnw` / `.git` の**どれか1つでも**最初に見つかったディレクトリで止まる
- **`gitTracked()` が効くかどうかは git に訊く**（`git rev-parse --is-inside-work-tree`）。
  ルート直下に `.git` があるかでは判定しない。**リポジトリのサブディレクトリにある Gradle プロジェクト**
  （モノレポの `repo/.git` と `repo/app/gradlew`、submodule、このリポジトリの `sample/` など）でも
  git フィルタは正しく効く。`git ls-files` をルートで実行すれば、そのサブツリーのファイルが
  ルートからの相対パスで返るため
- git が「work tree の中だ」と答えた後に `git ls-files` が失敗する場合はエラーにする
  （黙って全走査に落ちると手元と CI で結果が変わるため）。
  そもそも git 管理下でない / `git` コマンドが無い場合は `wholeTree()` として走査する
- `.git/` `.gradle/` `.idea/` は `files` の指定によらず、どの階層にあっても検査されない
- `gitTracked()` / `wholeTree()` は `me.tbsten.katachi.dsl` のトップレベル関数（`ArchitectureScope` を
  context parameter に取る）。`architecture { }` の中でだけ書けて、利用者が同じ書き方で自分のものを足せる
- **`FileSelection` は利用者が実装できる。** git 以外（Bazel、生成されたマニフェスト、社内ツール）が
  ファイル一覧を持っているなら、`FileSelection` を実装して `files` に渡す

## モジュール構成

| モジュール | 内容 |
|---|---|
| `:katachi` | 本体。**実行時依存ゼロ・JVM only**。座標は `me.tbsten.katachi:katachi` |
| `:katachi-konsist` | `konsist { }` 用の任意モジュール。座標は `me.tbsten.katachi:katachi-konsist` |
| `:architecture-test` | katachi 自身のアーキテクチャ定義。**公開しない** |

ルートプロジェクトは**サンプルの集約専用**で、プラグインもソースも持たない。
`./gradlew check` が Android SDK や Kotlin/Native ツールチェーン無しで通る状態を保つため。

## サンプル

`sample/` の下に3つ置いてある。どれも `settings.gradle.kts` と gradle wrapper を自前で持つ
**独立した Gradle ビルド**で、`includeBuild("../..")` で katachi をこのリポジトリのソースから取り込む。
利用者と同じ書き方（`testImplementation(libs.katachi)`）で使うので、結合テストを兼ねている。

| サンプル | 内容 | katachi の定義の置き場所 | ルートから回すタスク |
|---|---|---|---|
| `sample/jvm` | Ktor の最小サーバ（アプリ本体はルートプロジェクトの1モジュール） | `:architecture-test` | `./gradlew checkSampleJvm` |
| `sample/android` | マルチモジュールの Android アプリ（Compose / AndroidX の実依存あり） | `:app` の `src/test`（下記） | `./gradlew checkSampleAndroid` |
| `sample/kmp` | Android + iOS の KMP プロジェクト（Compose Multiplatform の実依存あり） | `:architecture-test` | `./gradlew checkSampleKmp` |

`:architecture-test` は上の「導入」で書いた推奨形そのもので、`kotlin("jvm")` と
`testImplementation(libs.katachi)` しか持たない。`sample/android` だけは現状 `:app` の
`src/test/kotlin` に間借りしていて、他の2つと形が揃っていない（→
`.local/features-by-version/v0.1/open-issues.md`）。

定義は**1ファイルではなく package で分けてある**。`application`（アプリ本体の役割）/
`testing`（テスト関連）/ `gradle`（ビルド設定）/ `tool`（git など）の4つで、
それぞれが非 inline の `ArchitectureScope` 拡張関数を公開し、`ProjectArchitecture.kt` はそれを呼ぶだけ。
**拡張関数に切り出しても宣言位置が呼び出し元ではなく定義を書いたファイルを指すこと**を、
各サンプルの `ProjectArchitectureSpec` がファイル名の完全一致で検証している。

スタブではなく**実物に近い中身**にしてある。`Screen` は本物の `@Composable`、`ViewModel` は本物の
`androidx.lifecycle.ViewModel` を継承し、`@Preview` も実際に書いてある。検査対象が実プロジェクトと同じ形でなければ、
katachi が実際の構成で機能することを確かめたことにならないため。

モジュールの切り方も実プロジェクト寄りで、`:ui` と `:data` は**1モジュールの中を package で分ける**形にしてある
（`:ui` は `component` / `theme` / `core` / `preview`、`:data` は android が `user` / `settings`、
kmp が `user` / `platform`）。katachi が表現できなければならない形の中で最もよく出てくるのがこれなので、
サンプルの主眼はここにある。

```bash
./gradlew check         # katachi 本体（:katachi）のテスト
./gradlew checkSamples  # 3サンプルすべて。各サンプルの gradlew を順に叩く
```

`checkSamples` は3サンプルを**順番に**回す（3つとも同じ katachi ビルドを共有していて、
並行させると katachi の `build/` が壊れるため）。個別に回したいときは各サンプルのディレクトリで
そのサンプルの `./gradlew` を直接叩いてもよい。

回すタスクは `-Pkatachi.sample.<name>.task=...`（全サンプルなら `-Pkatachi.sample.task=...`）で差し替えられる。
複数タスクはスペース区切りで書く。

`sample/jvm` と `sample/android` は `check`。`sample/kmp` だけは
`:architecture-test:test` と `:app:android:testDebugUnitTest` の**2つ**が既定になっている。

- `check` を使わないのは、KMP モジュールが iOS ターゲットを宣言していて、`check` が
  `compileKotlinIosArm64` と Kotlin/Native ツールチェーンのダウンロードを task graph に入れてしまうため
- それでも2つ回すのは、片方ずつでは足りないため。`:architecture-test:test` が katachi の検証で、
  `:app:android:testDebugUnitTest` が「サンプルが KMP プロジェクトとしてコンパイルできること」の検証。
  `:architecture-test` は素の JVM モジュールで `:ui` / `:data` / `:feature:*` を一切参照しない

### Android SDK

`sample/android` と `sample/kmp` は Android SDK を要求する。次のどちらかを用意する。

- 環境変数 `ANDROID_HOME`（または `ANDROID_SDK_ROOT`）を設定する — CI はこちら
- `sample/android/local.properties` / `sample/kmp/local.properties` に `sdk.dir=...` を書く
  （`local.properties` はマシン固有なのでコミットしない）

どちらも無い場合、ルートの `checkSample*` タスクは Android Studio の既定の SDK 位置
（`~/Library/Android/sdk` / `~/Android/Sdk`）を最後の手段として探す。

## 開発

| もの | バージョン |
|---|---|
| Gradle | 9.6.0 |
| Kotlin | 2.4.10 |
| JDK / toolchain | 17 |
| kotest | 6.2.5 |
| AGP（サンプル） | 9.1.0 — **上げないこと**（下記） |
| compileSdk / targetSdk / minSdk（サンプル） | 36 / 36 / 24 |
| Compose（サンプル） | android: BOM 2026.06.01 / kmp: Compose Multiplatform 1.10.3 |

- Kotlin / katachi / kotest のバージョンは `gradle/libs.versions.toml` が SSoT。
  3サンプルはこれを `libs` として読み、サンプル固有の依存（Ktor / AGP / Compose ランタイム）だけを
  自分の catalog（`sampleLibs`）に持つ
- **Compose コンパイラプラグイン**（`org.jetbrains.kotlin.plugin.compose`）は Kotlin と完全に同じバージョンでなければならず、
  TOML catalog は別の catalog を参照できない。そのためこれだけはルート catalog に
  `libs.plugins.kotlinPluginCompose` として置いてある。android / kmp のルート `build.gradle.kts` が
  `alias(libs.plugins.kotlinPluginCompose) apply false` で読む。
  Compose の**ランタイム**（BOM / Compose Multiplatform）は Kotlin と独立に決まるので、そちらは `sampleLibs` のまま
- **AGP は Android Studio 側の対応上限に合わせる。** 9.1.0 なのは、これより新しいと Android Studio の
  Gradle sync が `The project is using an incompatible version (AGP x.y.z) of the Android Gradle plugin.`
  で止まるため。CLI のビルドだけを見て上げると、IDE で開けなくなる。上げるときは
  [Android Studio と AGP の対応表](https://developer.android.com/build/releases/gradle-plugin#updating-gradle)を先に見る
- AGP 9 は Kotlin コンパイラを内蔵していて、放っておくと katachi より古い Kotlin でサンプルをコンパイルしてしまう。
  サンプルのルート `build.gradle.kts` がその版を引き上げている（理由はそのファイルのコメントに書いてある）。
  CI は `.github/scripts/check-kotlin-versions.sh` で、この回避策が効き続けているかを毎回突き合わせる
- **サンプルの Compose / AndroidX は「最新」ではなく「`minCompileSdk` が 36 以下で最新」を選ぶ。**
  AGP 9.1.0 が扱える `compileSdk` は 36 までだが、2026 年後半の AndroidX は `minCompileSdk=37` を宣言し始めている。
  Compose BOM 2026.08.00 以降・`lifecycle` 2.11.0・`navigation` 2.10.x・Compose Multiplatform 1.11.0 以降などを入れると
  configuration の時点で `requires ... version 37 or later of the Android APIs` で落ちる。
  同じ BOM でも artifact ごとに `minCompileSdk` が違うので、上げるときは
  `unzip -p <artifact>.aar META-INF/com/android/build/gradle/aar-metadata.properties` で1つずつ確かめる
- CI は `.github/workflows/ci.yml`。`main` への push と pull request で、本体と3サンプルをそれぞれ別ステップで回す
