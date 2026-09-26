# katachi

**プロジェクトの「どこに何を置けるか」を Kotlin DSL で1箇所に宣言し、その定義からアーキテクチャテスト・ドキュメント・コードの雛形を作る**ライブラリ。JVM / Android / KMP のどれでも同じ手順で使える。

[English](./README.md) | 日本語

**ドキュメント: https://tbsten.github.io/katachi/ja/**（English: https://tbsten.github.io/katachi/ ）

> [!NOTE]
> メジャーバージョンが 0 のあいだは、リリースに破壊的変更が入ることがある。v0.1 からの移行は [リリースノート](https://github.com/TBSten/katachi/releases) の v0.2.0 を参照。

## 1つの定義から、テスト・ドキュメント・コードが出る

役割（UseCase、Repository など）ごとに、置き場所と説明を書く。

```kotlin
"UseCase" {
    title = "ユースケース"
    summary = "各画面で発生するアプリ固有の1つの振る舞い"
    layout { "domain/src/main/kotlin/com/example/useCase" / "*UseCase".ktFile() }
    template { /* 新しい UseCase の雛形 */ }
}
```

この定義から、次の3つが出てくる。

- **テスト**: `projectArchitecture.assert()` が、宣言に無いファイル（`Unexpected`）と、宣言したのに実体が無いもの（`Missing`）を報告して落ちる。存在してよいものだけを宣言する **deny by default** なので、禁止事項を数え上げなくても「いつの間にか増えた置き場所」が残らない
- **ドキュメント**: `./gradlew katachiDocs` が、group と役割ごとの Markdown を書き出す。`--arg mode=check` を付ければ、コミット済みのドキュメントが古くなっていないかを CI で確かめられる
- **コード生成**: `./gradlew katachiTemplate --arg roleName=UseCase --arg name=GetUser` が、`layout { }` の決めた場所に雛形を置く

ファイルの置き場所だけでなく中身も、`konsist { }` で同じ定義に制約として書ける（[Konsist との統合](https://tbsten.github.io/katachi/ja/guides/konsist-integration/)）。

## 導入

アーキテクチャ定義とテストだけを置く JVM モジュールを1つ作り、そこに katachi を入れる。プロジェクトが Android でも KMP でも、このモジュールは素の `kotlin("jvm")` にする（katachi が JVM のライブラリなので）。

**必要なもの**: JDK 17 以降、Kotlin 2.2 以降、Gradle 8.0 以降（Gradle plugin を使う場合）。

### 1. モジュールを足し、プラグインの取得元に Maven Central を入れる

katachi の Gradle plugin は Gradle Plugin Portal ではなく Maven Central に公開している。`pluginManagement { }` に `mavenCentral()` が無いと解決できない。

```kotlin
// settings.gradle.kts
pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

include(":architecture-test")
```

### 2. 依存とプラグインを書く

```kotlin
// architecture-test/build.gradle.kts
plugins {
    // ルートの build.gradle.kts で `apply false` 済みなら、ここではバージョンを書かない
    kotlin("jvm") version "2.4.10"
    id("me.tbsten.katachi") version "0.2.0"
}

kotlin { jvmToolchain(17) }

tasks.test { useJUnitPlatform() }

dependencies {
    testImplementation("me.tbsten.katachi:katachi:0.2.0")
    // 任意。`konsist { }` を書くときだけ。
    testImplementation("me.tbsten.katachi:katachi-konsist:0.2.0")

    // katachi は AssertionError を投げるだけで、テストフレームワークに依存しない。
    // 実行エンジンは利用者が選ぶ（これは JUnit 5 の例）。
    testImplementation(platform("org.junit:junit-bom:5.13.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

katachi {
    // 3 で書くトップレベルの val の完全修飾名
    architecture = "com.example.projectArchitecture"
}
```

テストだけならプラグインは要らない。`katachiDocs` などのタスクを使うときに要る。

### 3. アーキテクチャを定義する

```kotlin
// architecture-test/src/test/kotlin/com/example/ProjectArchitecture.kt
package com.example

import me.tbsten.katachi.dsl.architecture
import me.tbsten.katachi.dsl.gradle.gradle
import me.tbsten.katachi.dsl.kotlin.ktFile

val projectArchitecture = architecture {
    // Gradle のファイル（wrapper・settings・各モジュールの build スクリプト・version catalog）をまとめて宣言する
    gradle()

    "domain".group {
        title = "ドメイン"
        "UseCase" {
            title = "ユースケース"
            summary = "各画面で発生するアプリ固有の1つの振る舞い"
            example("GetUserUseCase", "ユーザーを取得する")
            layout {
                "domain/src/main/kotlin/com/example/useCase" / "*UseCase".ktFile()
            }
            template {
                val name by stringParameter()
                file("${name}UseCase.kt") {
                    "package com.example.useCase\n\nclass ${name}UseCase\n"
                }
            }
        }
    }
}
```

### 4. テストを1本書き、実行する

```kotlin
// architecture-test/src/test/kotlin/com/example/ProjectArchitectureTest.kt
package com.example

import me.tbsten.katachi.check.assert
import org.junit.jupiter.api.Test

class ProjectArchitectureTest {
    @Test
    fun `構成が定義どおりになっている`() = projectArchitecture.assert()
}
```

```shell
./gradlew :architecture-test:test
```

**最初は、宣言していないファイルがすべて違反として出る。** 許可リスト方式なので、これが出発点になる。出てきたパスごとに、定義に足すか、ファイルを消すかを決めていく。違反はリポジトリ全体で1つの失敗メッセージにまとまるので、書くテストはこの1本だけでよい。

`konsist { }` を書いた場合は、引数なしの `assert()` では制約が評価されず、`[UncheckedFileConstraint]` で落ちる。制約もまとめて検査するには、次のように書く。

```kotlin
import me.tbsten.katachi.ExperimentalKatachiApi
import me.tbsten.katachi.check.FileConstraintCheck
import me.tbsten.katachi.check.assert
import org.junit.jupiter.api.Test

@OptIn(ExperimentalKatachiApi::class)
class ProjectArchitectureTest {
    @Test
    fun `構成が定義どおりになっている`() = projectArchitecture.assert(FileConstraintCheck())
}
```

### 5. ドキュメントとコードを生成する

```shell
# build/katachi/docs に Markdown を書き出す
./gradlew :architecture-test:katachiDocs

# domain/src/main/kotlin/com/example/useCase/GetUserUseCase.kt を作る
./gradlew :architecture-test:katachiTemplate --arg roleName=UseCase --arg name=GetUser
```

出力先の変え方、CI での古さの検査、テンプレートの引数は [ドキュメント生成](https://tbsten.github.io/katachi/ja/guides/document-generation/) と [テンプレートからコード生成](https://tbsten.github.io/katachi/ja/guides/generate-code-from-template/) にある。

AI Agent に導入を任せることもできる。手順は [初めてのアーキテクチャ定義](https://tbsten.github.io/katachi/ja/get-started/first-architecture/) を参照。

### テストが動かないときに確かめること

<details>
<summary><b>Kotlin 2.4 未満では <code>-Xcontext-parameters</code> が要る</b></summary>

DSL の入口（`module` / `mainSourceSet` / `ktFile` / `konsist` など）はすべて context parameters で宣言している。フラグが無いと1つも書けない。

```kotlin
kotlin {
    jvmToolchain(17)
    compilerOptions.freeCompilerArgs.add("-Xcontext-parameters")
}
```

Kotlin 2.4 以降では付けない。言語機能として入っているので、付けると redundant の警告が出て、`allWarningsAsErrors` のビルドが落ちる。

Kotlin 2.2 より古いと、すべてのシンボルが `Unresolved reference` になる。公開している artifact は `languageVersion` 2.2 でビルドしている。

</details>

<details>
<summary><b>JUnit の launcher とエンジンを忘れると、テストが起動しない・実行されない</b></summary>

- **`junit-platform-launcher` が無いと起動しない。** `junit-jupiter` の集約 artifact は api / params / engine を含むが launcher は含まず、Gradle 9 は自動で足さない。`Failed to load JUnit Platform` で落ちる
- **エンジンが無いと、テストは失敗せずに1度も実行されない。** `useJUnitPlatform()` だけでは `@Test` を拾う実装が classpath に無く、`BUILD SUCCESSFUL` のまま検査が空振りする。`build/test-results/**/*.xml` の `tests=` が 0 でないことで確かめられる
- kotest で書く場合は `kotest-runner-junit5` が自前のエンジンを持つので、それで足りる。ただし `kotest-runner-junit5` が連れてくるのは `junit-jupiter-api` だけなので、素の `@Test` を混ぜるなら `junit-jupiter`（engine 込み）が別に要る

</details>

<details>
<summary><b>なぜ既存モジュールの test に置かず、モジュールを1つ立てるのか</b></summary>

アーキテクチャ定義はプロジェクト全体を記述するもので、どのレイヤーにも属さない。だから `:app` やルートの test に間借りさせない。KMP ではそもそも間借り先が無い（katachi は JVM only なので `commonTest` には置けない）。

代償は、このモジュール自身も allow list に載ることだ。ただ「役割を持たないファイルは存在しない」という katachi の原則からすれば、むしろ載るべきものでもある。

</details>

## 定義の書き方

JUnit 4 / JUnit 5 / kotest のどれでも使える。`assert()` は `AssertionError` を投げるだけだからだ。

定義が大きくなったら、`DeclarationContainerScope` の拡張関数に切り出してファイルを分けられる（`architecture { }` と `"...".group { }` の中で呼べる）。違反メッセージが指す宣言位置は、呼び出し元ではなく、その宣言を書いたファイルになる。jvm / android / kmp の3つのサンプルがこの形で書かれていて、テストで確かめている。ただし分割に使う関数を `inline` にしてはいけない。`inline` にすると、宣言位置が呼び出し元ファイルの末尾より後ろの、存在しない行を指す。

役割・group・`layout { }` の詳しい書き方は、ドキュメントサイトの [基本的な API](https://tbsten.github.io/katachi/ja/guides/basic-api/)、[Role](https://tbsten.github.io/katachi/ja/guides/role/)、[layout](https://tbsten.github.io/katachi/ja/guides/layout/) にある。以下は、まだサイトに載っていない細則。

### layout の書き方

`layout { }` の直下はリポジトリルート。文字列にブロックを付けるとディレクトリ、`.file()` / `.ktFile()`（`.kt` を付ける）/ `.ktsFile()`（`.kts` を付ける）を付けるとファイルになる。入れ子ブロックと `/` 連結は同じ意味で、キーに `"src/main/kotlin"` のような多階層を書いてもよい。

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
- `ignore()` は `layout { }` の中にしか無い。「検査しない」と決めた理由が役割の `summary` として生成ドキュメントに残るようにするためで、グローバルな除外設定は用意していない

### glob

katachi の glob は **`*` と `**` の2つだけ**。`{a,b}` / `?` / `[abc]` はワイルドカードとして働かず、書くとエラーになる。文字そのものとして書きたければ `\*` のようにバックスラッシュでエスケープする。

| 書き方 | 意味 |
|---|---|
| `*` | ちょうど1階層。`:feature:*` は `:feature:home` にマッチし、`:feature` にも `:feature:home:impl` にもマッチしない |
| `**` | 0階層以上。`:feature:**` は `:feature` / `:feature:home` / `:feature:home:impl` のすべてにマッチする |
| ファイル名の中の `*` | 名前の一部にマッチし、**ディレクトリの境界を越えない**。`*UseCase.kt` は `GetUserUseCase.kt` にマッチし、サブディレクトリの中の同名ファイルにはマッチしない |

- `*` は**0文字にはマッチしない**（`*UseCase.kt` は `UseCase.kt` にマッチしない）
- 照合は**常に大文字小文字を区別する**
- ディレクトリのパスでもモジュールパス（`:feature:home`）でも、`*` / `**` の意味は同じ
- **ファイルの位置に `**` だけを書かない。** `"src/test/kotlin/**".file()` は `src/test/kotlin` までしか「既知のディレクトリ」にならず、`src/test/kotlin/com` が `[UnexpectedDirectory]` になる。`"src/test/kotlin" / "**" / "*".ktFile()` と書く
- **パスの先頭に `**` を置かない。** `"**/build".ignore()` は `**`（任意のパス）自体を既知のディレクトリとして登録してしまい、`[UnexpectedDirectory]` が一切出なくなる

### 検査するファイル

既定では、**git が「このプロジェクトのファイル」と答えたものだけ**を検査する（`git ls-files --cached --others --exclude-standard` の結果。追跡中のものと、未追跡だが無視されていないもの）。`build/` や `.DS_Store`、`local.properties` に役割を与える必要はない。

```kotlin
architecture {
    files = gitTracked()      // 既定。書かなくてもこれ
    // files = wholeTree()    // git を見ず、ファイルツリーをそのまま走査する
}
```

- **既定では `git` コマンドをプロジェクトルートで起動する**（`rev-parse --is-inside-work-tree` で可否を判定し、`ls-files` を1回）
- プロジェクトルートは Konsist と同じ方式で決める。作業ディレクトリから上へ辿り、`gradlew` / `mvnw` / `.git` の**どれか1つでも**最初に見つかったディレクトリで止まる
- **`gitTracked()` が効くかどうかは git に訊く。** ルート直下に `.git` があるかでは判定しない。モノレポ（`repo/.git` と `repo/app/gradlew`）、submodule、worktree、このリポジトリの `sample/` のように、リポジトリのサブディレクトリにある Gradle プロジェクトでも git のフィルタは正しく効く
- git が「work tree の中だ」と答えた後に `git ls-files` が失敗した場合はエラーにする。黙って全走査に切り替えると、手元と CI で結果が変わるからだ。そもそも git 管理下でない場合や `git` コマンドが無い場合は、`wholeTree()` として走査する
- `.git/` `.gradle/` `.idea/` は `files` の指定によらず、どの階層にあっても検査しない
- `gitTracked()` / `wholeTree()` は `me.tbsten.katachi.dsl` のトップレベル関数で、`ArchitectureScope` を context parameter に取る。`architecture { }` の中でだけ書け、利用者も同じ形で自分のものを足せる
- **`FileSelection` は利用者が実装できる。** Bazel、生成されたマニフェスト、社内ツールなど、git 以外がファイル一覧を持っているなら、`FileSelection` を実装して `files` に渡す

## サンプル

`sample/` の下に4つある。それぞれの書き味と実行方法は各 README に、サンプル全体に共通する設計方針とビルド設定は [`sample/README.md`](sample/README.md) にまとめてある。

| サンプル | 内容 |
|---|---|
| [`sample/jvm`](sample/jvm/README.md) | Ktor の最小サーバ |
| [`sample/android`](sample/android/README.md) | マルチモジュールの Android アプリ（Compose / AndroidX の実依存あり） |
| [`sample/kmp`](sample/kmp/README.md) | Android + iOS の KMP プロジェクト（Compose Multiplatform の実依存あり） |
| [`sample/custom-processor`](sample/custom-processor/README.md) | 自分で processor を書くときの見本 |

jvm / android / kmp は、プラグイン・`gradle()`・`template { }` を使い、`katachiDocs` で生成したドキュメントをコミットしている。

## 公開しているもの

| artifact | 内容 |
|---|---|
| `me.tbsten.katachi:katachi` | 本体。DSL・検査・ドキュメント生成・テンプレート。**JVM only**。実行時の依存は `kotlinx-serialization-core` だけ |
| `me.tbsten.katachi:katachi-konsist` | `konsist { }` 用の任意モジュール |
| Gradle plugin `me.tbsten.katachi` | processor ごとのタスク（`katachiDocs` / `katachiTemplate` / `katachiTemplates` など）を足す。同じ版の `:katachi` と組み合わせて使う |

## 開発

| もの | バージョン |
|---|---|
| Gradle | 9.6.0 |
| Kotlin | 2.4.10 |
| JDK / toolchain | 17 |
| kotest | 6.2.5 |

リポジトリの中身:

| 場所 | 内容 |
|---|---|
| `katachi/` / `katachi-konsist/` / `katachi-gradle-plugin/` | 公開する3つのモジュール |
| `architecture-test/` | katachi 自身のアーキテクチャ定義。公開しない |
| `tool/dokka/` | API リファレンスを作る Dokka plugin。公開しない |
| `sample/` | 独立した Gradle ビルドとして動くサンプル |
| `docs/` | ドキュメントサイト |

```shell
./gradlew check          # 本体の検査。Android SDK も Kotlin/Native も要らない
./gradlew checkSamples   # 全サンプルを、それぞれの wrapper で順に回す
./gradlew checkSampleJvm # 1つだけ回す（Android / KMP のサンプルは Android SDK が要る）
```

- サンプルはルートのサブプロジェクトではなく独立したビルドなので、`./gradlew check` には入らない。ルートプロジェクトはソースを持たず、API リファレンスの集約とサンプルを回すタスクだけを持つ
- Kotlin / katachi / kotest のバージョンは `gradle/libs.versions.toml` が SSoT。サンプルはこれを `libs` として読み、サンプル固有の依存は自分の catalog（`sampleLibs`）に持つ（詳細は [`sample/README.md`](sample/README.md)）
- CI は `.github/workflows/ci.yml`。`main` への push と pull request で、本体と4つのサンプルをそれぞれ別のステップで回す

## ライセンス

MIT。[LICENSE](./LICENSE) を参照。
