# katachi

**Android / KMP プロジェクトのアーキテクチャを Kotlin DSL で書き、同じ定義から「テスト」と「ドキュメント」の両方を出す**ためのライブラリ。

[English](./README.md) | 日本語

v0.1 は **Deny by default のアーキテクチャテスト**として出している。
「どの役割のファイルをどこに置けるか」を1箇所に宣言し、宣言に載っていないファイル（`Unexpected`）と、
宣言されているのに実体が無いもの（`Missing`）をテストで検出する。
ドキュメント生成は v0.2 の予定。

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

`sample/` の下に4つ置いてある。それぞれの書き味・実行方法は各 README、サンプル全体に共通する
設計方針やビルド設定は [`sample/README.md`](sample/README.md) にまとめてある。

| サンプル | 内容 | README |
|---|---|---|
| `sample/jvm` | Ktor の最小サーバ | [`sample/jvm/README.md`](sample/jvm/README.md) |
| `sample/android` | マルチモジュールの Android アプリ（Compose / AndroidX の実依存あり） | [`sample/android/README.md`](sample/android/README.md) |
| `sample/kmp` | Android + iOS の KMP プロジェクト（Compose Multiplatform の実依存あり） | [`sample/kmp/README.md`](sample/kmp/README.md) |
| `sample/custom-processor` | 利用者が自分で書く processor の見本 | [`sample/custom-processor/README.md`](sample/custom-processor/README.md) |

```bash
./gradlew checkSamples  # 全サンプルをまとめて回す
```

## 開発

| もの | バージョン |
|---|---|
| Gradle | 9.6.0 |
| Kotlin | 2.4.10 |
| JDK / toolchain | 17 |
| kotest | 6.2.5 |

- Kotlin / katachi / kotest のバージョンは `gradle/libs.versions.toml` が SSoT。サンプルはこれを
  `libs` として読み、サンプル固有の依存は自分の catalog（`sampleLibs`）に持つ
  （サンプルのビルド設定の詳細は [`sample/README.md`](sample/README.md) 参照）
- CI は `.github/workflows/ci.yml`。`main` への push と pull request で、本体と4サンプルをそれぞれ別ステップで回す
