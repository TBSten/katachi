# katachi

[![Maven Central](https://img.shields.io/maven-central/v/me.tbsten.katachi/katachi)](https://central.sonatype.com/artifact/me.tbsten.katachi/katachi)
[![CI](https://github.com/TBSten/katachi/actions/workflows/ci.yml/badge.svg)](https://github.com/TBSten/katachi/actions/workflows/ci.yml)
[![License](https://img.shields.io/badge/license-MIT-blue)](./LICENSE)

**「どのファイルをどこに置くか」を Kotlin で定義し、そこからアーキテクチャテスト・ドキュメント・コードの雛形を作るライブラリです。**

[English](./README.md) | 日本語 ・ [ドキュメント](https://tbsten.github.io/katachi/ja/)

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

## モジュール

| モジュール | 内容 |
|---|---|
| `me.tbsten.katachi:katachi` | 本体（JVM） |
| `me.tbsten.katachi:katachi-konsist` | `konsist { }` を使うときだけ |
| Gradle plugin `me.tbsten.katachi` | `katachiDocs` / `katachiTemplate` などのタスク。同じ版の本体と組み合わせます |

## Contribution

[CONTRIBUTING.ja.md](CONTRIBUTING.ja.md) を参照してください。

## ライセンス

[MIT](./LICENSE)
