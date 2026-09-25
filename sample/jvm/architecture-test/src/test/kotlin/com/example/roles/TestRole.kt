package com.example.roles

import me.tbsten.katachi.dsl.DeclarationContainerScope
import me.tbsten.katachi.dsl.gradle.*
import me.tbsten.katachi.dsl.kotlin.ktFile

/** The role of code that asserts behaviour, mirroring the main source set. */
fun DeclarationContainerScope.test() = "Test" {
    title = "テストコード"
    summary = "src/test/kotlin に置かれるテスト。本体と同じ package 構成を保つ"
    description = """
        アプリケーションの振る舞いを確かめるコードです。対象と同じ package に置き、
        `src/test/kotlin` の下が `src/main/kotlin` の鏡像になるようにします。

        `HealthRouteTest` は kotest の `FreeSpec` で書かれ、Ktor の `testApplication` を立てて
        `GET /health` に実際にリクエストを投げます。テスト名は日本語の1文で、何を確かめるのかを
        そのまま書きます。レイアウトのチェックが通るだけで中身が死んでいる、という状態に
        しないための押さえです。

        ここに入らないもの:

        - アーキテクチャ定義そのもののテスト。それらは `:architecture-test` にあり、
          アーキテクチャ定義の役割が覆います。この役割が見ているのはルートプロジェクト（`:`）の
          テストソースセットだけです

        `layout { }` はファイル名を縛っていません（`**` がパッケージの階層、その下の `*` が
        任意の `.kt` 1ファイル）。代わりに、`.kt` を1つも持たないディレクトリがテストソースセットの
        下に残っていれば報告されます。
    """.trimIndent()
    example("HealthRouteTest", "GET /health のテスト")
    layout {
        // `**` stands for the package levels, which mirror the main source set and are
        // not worth writing twice — so `modulePackage` is deliberately not used here.
        // The `*` after it is one file name, so a directory holding no `.kt` at all is
        // still reported.
        ":".module {
            testSourceSet / kotlin / "**" / "*".ktFile()
        }
    }
}
