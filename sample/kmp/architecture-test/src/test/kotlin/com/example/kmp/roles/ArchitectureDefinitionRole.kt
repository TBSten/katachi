package com.example.kmp.roles

import me.tbsten.katachi.dsl.DeclarationContainerScope
import me.tbsten.katachi.dsl.gradle.*
import me.tbsten.katachi.dsl.kotlin.ktFile

/**
 * The definition this very file is part of.
 *
 * It is not test code and belongs to no layer of the app, so it gets a role of its own instead
 * of hiding inside [test]: "a file with no role does not exist" applies to katachi's own module
 * too.
 */
fun DeclarationContainerScope.architectureDefinition() = "ArchitectureDefinition" {
    title = "アーキテクチャ定義"
    summary = ":architecture-test モジュールの src/test。katachi の DSL で書いた" +
        "このプロジェクトの定義。どのレイヤーにも属さないので専用モジュールに置く"
    description = """
        katachi でこのプロジェクトの形を書いたコードです。いま読んでいるファイルもこの役割に
        属します。テストコードとは分けてあります。定義の仕事はプロジェクトを説明することで、
        それを実際のディレクトリと突き合わせるテスト（`ProjectLayoutSpec`）は1行しかありません。

        専用の `:architecture-test` モジュールに置いているのは KMP だからです。katachi は JVM の
        ライブラリで、このビルドの他のモジュールは Android と iOS だけを持つ KMP なので、
        定義を置ける `commonTest` がどこにもありません。1つだけ `kotlin("jvm")` のモジュールを
        用意する、というのが katachi の推奨する形でもあります。

        宣言1つにつきファイル1つ、という規則で並べます。役割 `"UiCore"` は
        `roles/UiCoreRole.kt`、group `"build"` は `groups/BuildGroup.kt`。
        `ProjectArchitectureSpec` はその規則自体を検査していて、宣言位置をソースから読み戻すので、
        group / role の関数に `inline` が付くと落ちます。

        置いてよいもの:

        - 定義（`groups` / `roles` package）と、その入口の `ProjectArchitecture.kt`
        - 定義を検査する `*Spec.kt`
        - `processor` package の自作プロセッサ（`owner` のような独自メタデータを読むもの）

        置いてはいけないもの:

        - アプリのコード。このモジュールはアプリのどのレイヤーにも属しません

        layout はわざと緩く、package の1段を `*` で受けています。`roles` に新しいファイルを
        足しても定義を触らずに通る、という側です。厳しく package 名まで書く形は
        sample/android の方にあり、両方あることで選べることが見えます。
    """.trimIndent()
    example("ProjectArchitecture.kt", "定義の入口")
    example("roles/ComponentRole.kt", "役割1つの宣言")
    // Two patterns: the entry point and the specs sit in `com/example/kmp` itself, and each
    // concern gets one package below it — `groups`, `roles`, `processor`. The `*` in the
    // middle is that package.
    //
    // Deliberately left this loose rather than naming the two packages: a file dropped
    // into `roles` under any name still passes here. sample/android is the one that writes
    // the stricter form, and having one of each is what shows the choice exists.
    //
    // The package is written out rather than derived: `modulePackage` would turn
    // `:architecture-test` into `com/example/kmp/architectureTest`, and this module
    // deliberately holds `com.example.kmp` itself, next to nothing else.
    layout {
        ":architecture-test".module {
            testSourceSet / kotlin / "com/example/kmp" / "*".ktFile()
            testSourceSet / kotlin / "com/example/kmp" / "*" / "*".ktFile()
        }
    }
}
