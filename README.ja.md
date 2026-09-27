# katachi

[![Maven Central](https://img.shields.io/maven-central/v/me.tbsten.katachi/katachi)](https://central.sonatype.com/artifact/me.tbsten.katachi/katachi)
[![CI](https://github.com/TBSten/katachi/actions/workflows/ci.yml/badge.svg)](https://github.com/TBSten/katachi/actions/workflows/ci.yml)
[![License](https://img.shields.io/badge/license-MIT-blue)](./LICENSE)

**「どのファイルをどこに置くか」を Kotlin で定義し、そこからアーキテクチャテスト・ドキュメント・コードの雛形を作るライブラリです。**

[English](./README.md) | 日本語 ・ [ドキュメント](https://tbsten.github.io/katachi/ja/)

> [!NOTE]
> 0.x のあいだは、リリースに破壊的変更が入ることがあります。

## できること

```kotlin
"UseCase" {
    summary = "各画面で発生するアプリ固有の1つの振る舞い"
    layout { "domain/src/main/kotlin/com/example/useCase" / "*UseCase".ktFile() }
    template { /* 新しい UseCase の雛形 */ }
}
```

この定義1つから、次の3つが手に入ります。

- **テスト**: 定義に無い場所のファイルを報告します。定義したものだけを許す方式なので、置き場所がいつの間にか増えません
- **ドキュメント**: `./gradlew katachiDocs` が役割ごとの Markdown を書き出します
- **コードの雛形**: `./gradlew katachiTemplate --arg roleName=UseCase --arg name=GetUser` が、定義どおりの場所にファイルを作ります

ファイルの中身の規則（「public であること」など）も `konsist { }` で書けます。Konsist・detekt・ArchUnit との違いは [他ツールとの比較](https://tbsten.github.io/katachi/ja/get-started/comparison-with-other-tools/) にあります。

**対応**: JVM / Android / KMP のプロジェクト。定義とテストは JVM のモジュールに置きます。JDK 17+・Kotlin 2.2+・Gradle 8.0+。

## はじめる

アーキテクチャ定義のための JVM モジュールを1つ作ります。

```kotlin
// settings.gradle.kts — plugin は Maven Central に公開しています
pluginManagement { repositories { gradlePluginPortal(); mavenCentral() } }
include(":architecture-test")
```

```kotlin
// architecture-test/build.gradle.kts
plugins {
    kotlin("jvm") version "2.4.10"
    id("me.tbsten.katachi") version "0.2.0"
}

kotlin { jvmToolchain(17) }
tasks.test { useJUnitPlatform() }

dependencies {
    testImplementation("me.tbsten.katachi:katachi:0.2.0")
    testImplementation(platform("org.junit:junit-bom:5.14.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

katachi { architecture = "com.example.projectArchitecture" }
```

定義とテストを書きます。

```kotlin
// architecture-test/src/test/kotlin/com/example/ProjectArchitecture.kt
val projectArchitecture = architecture {
    gradle() // wrapper・settings・build スクリプトなど Gradle のファイル
    "domain".group {
        "UseCase" {
            layout { "domain/src/main/kotlin/com/example/useCase" / "*UseCase".ktFile() }
        }
    }
}

// architecture-test/src/test/kotlin/com/example/ProjectArchitectureTest.kt
class ProjectArchitectureTest {
    @Test
    fun `構成が定義どおりになっている`() = projectArchitecture.assert()
}
```

```shell
./gradlew :architecture-test:test           # 検査する
./gradlew :architecture-test:katachiDocs    # ドキュメントを build/katachi/docs に書き出す
```

最初は、定義していないファイルがすべて違反として出ます。出てきたパスごとに、定義に足すかファイルを消すかを決めていきます。

手順の全体（import・テンプレート・AI Agent に任せる方法）は [初めてのアーキテクチャ定義](https://tbsten.github.io/katachi/ja/get-started/first-architecture/) にあります。

<details>
<summary><b>うまく動かないとき</b></summary>

- **Kotlin 2.4 未満**: `compilerOptions.freeCompilerArgs.add("-Xcontext-parameters")` が要ります（2.4 以降では付けないでください。警告になります）
- **`Failed to load JUnit Platform`**: `junit-platform-launcher` が足りません
- **テストが1件も実行されない**: JUnit のエンジン（`junit-jupiter`）が足りません。`BUILD SUCCESSFUL` のまま検査が空振りします
- **`konsist { }` を書いたら `[UncheckedFileConstraint]` で落ちる**: `assert(FileConstraintCheck())` と書きます（`@OptIn(ExperimentalKatachiApi::class)` が要ります）

</details>

## もっと知る

| やりたいこと | ページ |
|---|---|
| 役割・group の書き方 | [基本的な API](https://tbsten.github.io/katachi/ja/guides/basic-api/)、[Role](https://tbsten.github.io/katachi/ja/guides/role/) |
| 置き場所の書き方 | [Layout](https://tbsten.github.io/katachi/ja/guides/layout/) |
| ファイルの中身も検査する | [Konsist との統合](https://tbsten.github.io/katachi/ja/guides/konsist-integration/) |
| ドキュメント・コードを生成する | [ドキュメント生成](https://tbsten.github.io/katachi/ja/guides/document-generation/)、[テンプレートからコード生成](https://tbsten.github.io/katachi/ja/guides/generate-code-from-template/) |
| 実際の定義を見る | [サンプル](CONTRIBUTING.ja.md#サンプル)、[レシピ](https://tbsten.github.io/katachi/ja/recipes/) |

### まだサイトに載っていない細則

<details>
<summary><b><code>layout { }</code> の書き方</b></summary>

`layout { }` の直下はリポジトリルートです。文字列にブロックを付けるとディレクトリ、`.file()` / `.ktFile()`（`.kt` を付ける）/
`.ktsFile()`（`.kts` を付ける）を付けるとファイルになります。入れ子ブロックと `/` 連結は同じ意味で、キーに `"src/main/kotlin"`
のような多階層を書いても構いません。

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

- **空のディレクトリブロックは「何も置けない」という意味です**。`"di" { }` の配下にファイルがあれば `Unexpected` になります
- **宣言したのに実体が無いファイルは `Missing` になります**。`.optional()` を付けると出なくなります
- **ワイルドカードを含む宣言は自動で optional です**。0件マッチでも `Missing` になりません
- `anyFile()` が許すのは **直下だけ**です。サブディレクトリの中のファイルは `Unexpected` のままです
- `ignore()` は `layout { }` の中にしかありません。「検査しない」と決めた理由が役割の `summary`
  として生成ドキュメントに残るようにするためで、グローバルな除外設定は用意していません

</details>

<details>
<summary><b>glob（<code>*</code> と <code>**</code>）</b></summary>

katachi の glob は **`*` と `**` の2つだけ**です。`{a,b}` / `?` / `[abc]` はワイルドカードとして働かず、書くとエラーになります。文字そのものとして書きたいときは
`\*` のようにバックスラッシュでエスケープします。

| 書き方               | 意味                                                                                                                                                        |
|----------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `*`                  | ちょうど1階層。`:feature:*` は `:feature:home` にマッチし、`:feature` にも `:feature:home:impl` にもマッチしません                                          |
| `**`                 | 0階層以上。`:feature:**` は `:feature` / `:feature:home` / `:feature:home:impl` のすべてにマッチします                                                      |
| ファイル名の中の `*` | 名前の一部にマッチし、**ディレクトリの境界を越えません**。`*UseCase.kt` は `GetUserUseCase.kt` にマッチし、サブディレクトリの中の同名ファイルにはマッチしません |

- `*` は **0文字にはマッチしません**（`*UseCase.kt` は `UseCase.kt` にマッチしません）
- 照合は **常に大文字小文字を区別します**
- ディレクトリのパスでもモジュールパス（`:feature:home`）でも、`*` / `**` の意味は同じです
- **ファイルの位置に `**` だけを書かないでください。** `"src/test/kotlin/**".file()` は `src/test/kotlin` までしか「既知のディレクトリ」にならず、
  `src/test/kotlin/com` が `[UnexpectedDirectory]` になります。`"src/test/kotlin" / "**" / "*".ktFile()` と書きます
- **パスの先頭に `**` を置かないでください。** `"**/build".ignore()` は `**`（任意のパス）自体を既知のディレクトリとして登録してしまい、
  `[UnexpectedDirectory]` が一切出なくなります

</details>

<details>
<summary><b>どのファイルを検査するか</b></summary>

既定では、 **git が「このプロジェクトのファイル」と答えたものだけ**を検査します（
`git ls-files --cached --others --exclude-standard` の結果。追跡中のものと、未追跡だが無視されていないもの）。`build/` や
`.DS_Store`、`local.properties` に役割を与える必要はありません。

```kotlin
architecture {
    files = gitTracked()      // 既定。書かなくてもこれ
    // files = wholeTree()    // git を見ず、ファイルツリーをそのまま走査する
}
```

- **既定では `git` コマンドをプロジェクトルートで起動します**（`rev-parse --is-inside-work-tree` で可否を判定し、`ls-files`
  を1回）
- プロジェクトルートは Konsist と同じ方式で決めます。作業ディレクトリから上へ辿り、`gradlew` / `mvnw` / `.git` の
  **どれか1つでも**最初に見つかったディレクトリで止まります
- **`gitTracked()` が効くかどうかは git に訊きます。** ルート直下に `.git` があるかでは判定しません。モノレポ（`repo/.git` と
  `repo/app/gradlew`）、submodule、worktree、このリポジトリの `sample/` のように、リポジトリのサブディレクトリにある Gradle
  プロジェクトでも、git のフィルタは正しく効きます
- git が「work tree の中だ」と答えた後に `git ls-files` が失敗した場合はエラーにします。黙って全走査に切り替えると、手元と CI
  で結果が変わるからです。そもそも git 管理下でない場合や `git` コマンドが無い場合は、`wholeTree()` として走査します
- `.git/` `.gradle/` `.idea/` は `files` の指定によらず、どの階層にあっても検査しません
- `gitTracked()` / `wholeTree()` は `me.tbsten.katachi.dsl` のトップレベル関数で、`ArchitectureScope` を context
  parameter に取ります。`architecture { }` の中でだけ書け、利用者も同じ形で自分のものを足せます
- **`FileSelection` は利用者が実装できます。** Bazel、生成されたマニフェスト、社内ツールなど、git 以外がファイル一覧を持っているなら、
  `FileSelection` を実装して `files` に渡します

</details>

## 公開しているもの

| artifact | 内容 |
|---|---|
| `me.tbsten.katachi:katachi` | 本体（JVM） |
| `me.tbsten.katachi:katachi-konsist` | `konsist { }` を使うときだけ |
| Gradle plugin `me.tbsten.katachi` | `katachiDocs` / `katachiTemplate` などのタスク。同じ版の本体と組み合わせます |

## 開発に参加する

[CONTRIBUTING.ja.md](CONTRIBUTING.ja.md) を参照してください。

## ライセンス

[MIT](./LICENSE)
