---
title: 導入する
description: katachi をプロジェクトに入れる。プロジェクトの種別によらず手順は同じ。
---

katachi の導入手順は **Android / KMP / JVM のどれでも同じ4ステップ**です。

## 1. アーキテクチャ定義用のモジュールを作る

アーキテクチャ定義は、アプリのどのレイヤーにも属しません。
専用の JVM モジュールを1つ作って、そこに置きます。

```kotlin title="settings.gradle.kts"
include(":architecture-test")
```

```kotlin title="architecture-test/build.gradle.kts"
plugins {
    kotlin("jvm")
}

dependencies {
    testImplementation("me.tbsten.katachi:katachi:0.1.0")
}
```

:::note[なぜ専用モジュールなのか]
既存のモジュール（`:app` など）に間借りすることもできますが、推奨しません。

- **KMP では `commonTest` に置けません。** katachi は JVM ライブラリなので、
  JVM ターゲットの無いプロジェクトでは置き場所に困ります
- **Android で `:app` に置くのは恣意的です。** アーキテクチャ定義は app の一部ではありません

専用モジュールなら、プロジェクトの種別によらず同じ形になります。
:::

## 2. 役割を宣言する

`architecture { }` の中に、group と役割を書きます。

```kotlin title="architecture-test/src/test/kotlin/.../ProjectArchitecture.kt"
val projectArchitecture = architecture {
    "domain".group {
        title = "ドメイン"

        "UseCase" {
            title = "ユースケース"
            summary = "各画面で発生するアプリ固有の1つの振る舞い"
            example("GetUserUseCase", "ユーザーを取得する")

            layout {
                ":core:domain".module {
                    mainSourceSet / kotlin / modulePackage / "useCase" / "*UseCase".ktFile()
                }
            }
        }
    }
}
```

定義が大きくなったら、`ArchitectureScope` の拡張関数でファイルを分けられます。

```kotlin title="architecture-test/src/test/kotlin/.../domain/DomainRoles.kt"
fun ArchitectureScope.domainRoles() {
    "domain".group { /* ... */ }
}
```

```kotlin title="ProjectArchitecture.kt"
val projectArchitecture = architecture {
    domainRoles()
    uiRoles()
}
```

:::caution[分割用の関数を `inline` にしないこと]
違反メッセージが示す宣言位置は `Throwable().stackTrace` から取っています。
`inline` にすると、呼び出し元ファイルの末尾より後ろの**存在しない行**を指すようになります。
:::

## 3. テストを1つ書く

```kotlin title="architecture-test/src/test/kotlin/.../ProjectArchitectureTest.kt"
class ProjectArchitectureTest {
    @Test
    fun `構成が allow list に従っている`() = projectArchitecture.assert()
}
```

JUnit4 / JUnit5 / kotest のどれでも動きます（`AssertionError` を投げるだけです）。

**テストは1つにまとめます。** 違反が複数あっても1回のレポートに全部出ます。
テストを分けると「1件直す → 再実行 → 次が出る」の繰り返しになって遅くなります。

## 4. 走らせる

```bash
./gradlew :architecture-test:test
```

宣言に載っていないファイルがあれば、こういうメッセージで落ちます。

```
Katachi check failed: 2 violations (Unexpected: 1, Missing: 1)

[UnexpectedFile] core/domain/src/main/kotlin/com/example/core/domain/TokenRefresher.kt
  No role is defined for this file.
  Nearby locations:
    UseCase    core/domain/src/main/kotlin/com/example/core/domain/useCase/
  How to fix:
    - Move it to one of the locations above
    - Delete it if it is not needed
    - Add a new role to ProjectArchitecture.kt
```

## 中身の制約も書きたいとき

`.kt` の中身の検査は Konsist に委譲します。別 artifact なので、使うときだけ足します。

```kotlin
testImplementation("me.tbsten.katachi:katachi-konsist:0.1.0")
```

```kotlin
"UseCase" {
    "invoke を持つこと".konsist {
        classes()
            .withNameEndingWith("UseCase")
            .assertTrue { it.hasFunction { f -> f.name == "invoke" } }
    }
}
```

配置の違反と中身の違反が、同じ1つのレポートに並びます。
