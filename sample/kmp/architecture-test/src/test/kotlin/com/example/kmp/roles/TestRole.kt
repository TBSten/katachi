package com.example.kmp.roles

import me.tbsten.katachi.dsl.DeclarationContainerScope
import me.tbsten.katachi.dsl.gradle.*
import me.tbsten.katachi.dsl.kotlin.ktFile

/**
 * The test code of the app itself.
 *
 * Android puts its tests in `src/test`; a KMP module puts them in `commonTest`. Both shapes
 * are one role here.
 */
fun DeclarationContainerScope.test() = "Test" {
    title = "テストコード"
    summary = "各モジュールのテスト。KMP モジュールは commonTest、" +
        "純 Android / 純 JVM モジュールは src/test"
    description = """
        アプリ自身のテストです。置き場所はモジュールの種類で決まります。Android モジュールは
        `src/test`、KMP モジュールなら `commonTest`。どちらも「そのモジュールのテスト」で
        あることは変わらないので、katachi の上では1つの役割にしています。

        いま layout に書いてあるのは `:app:android` の `testSourceSet` だけです。実際に
        テストを持つモジュールがそこしかないからで、`commonTest` を持つモジュールが現れたら
        そのとき1行足します。無いディレクトリを先に宣言すると、このサンプルが持っていない形を
        持っていると言うことになります。

        テストが `:app:android` に集まっているのは KMP ゆえの事情です。他のモジュールは
        Android と iOS だけを持つ KMP で JVM ターゲットがないので、Compose を使わない部分
        （`Navigator`、`UiState`、`FakeUserRepository`）を回せる JVM のテストがここしか
        ありません。

        置いてはいけないもの:

        - アーキテクチャ定義。`:architecture-test` は ArchitectureDefinition の役割です
        - テストダブル。`Fake*` は `:testing` の commonMain にあります

        画面の `@Composable` はテストしていません。Compose のテストランタイムが要る話になり、
        このサンプルが見せたい範囲の外です。
    """.trimIndent()
    example("SampleModulesSpec", ":app:android のユニットテスト")
    // Only `:app:android` has test code of its own today, and it is an Android
    // module, so `testSourceSet` is the one place declared. A KMP module would add
    // `"commonTest".sourceSet`; no module in this sample has one yet, and a path
    // declared for a directory that does not exist would claim a shape the sample
    // does not have.
    layout {
        ":app:android".module {
            testSourceSet / kotlin / "com/example/kmp/app" / "*Spec".ktFile()
        }
    }
}
