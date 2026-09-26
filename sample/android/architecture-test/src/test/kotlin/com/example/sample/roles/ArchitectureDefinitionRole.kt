package com.example.sample.roles

import me.tbsten.katachi.dsl.DeclarationContainerScope
import me.tbsten.katachi.dsl.gradle.*
import me.tbsten.katachi.dsl.kotlin.ktFile

/** The role of the katachi definition itself, which belongs to no layer of the application. */
fun DeclarationContainerScope.architectureDefinition() = "ArchitectureDefinition" {
    title = "アーキテクチャ定義"
    summary = "katachi の DSL で書かれた役割の定義。どのレイヤーにも属さない"
    description = """
        katachi の DSL で書かれた、この定義そのもの。`:architecture-test` という、
        アプリのどのレイヤーにも属さない素の `kotlin("jvm")` モジュールに置く。
        Android のモジュールではないのは、katachi が JVM のライブラリで、
        プロジェクトの種別によらず同じ形にできるから。

        1宣言1ファイルで、ファイル名がその種類を表す。`ProjectArchitecture.kt` が入口で
        group の関数を呼ぶだけ、`groups/<Name>Group.kt` が group を1つ、
        `roles/<Name>Role.kt` が役割を1つ。`groups/` と `roles/` には `*Group.kt` `*Role.kt` しか
        置けないので、共有のヘルパーが3つ目の種類として紛れ込むと `[UnexpectedFile]` になる。
        唯一の例外が `DocumentSections.kt` で、全 group・全役割が `by` で使う節の定義だけを
        1ファイルにまとめてあるので、`ProjectArchitecture.kt` と同じく名指しで許してある。

        拡張関数を `inline` にしてはいけない。katachi は宣言位置をスタックトレースから取るので、
        inline すると呼び出し元ファイルの、誰も書いていない行を指すようになる。
        `ProjectArchitectureSpec` がその行を実際に読み戻して見張っている。

        `:architecture-test` は `:app` と並ぶ、package がモジュールパスから導けない
        モジュール。そのまま当てると `com/example/sample/architectureTest` になってしまうので、
        この役割とテストコード役割はどちらも `com/example/sample` を直接書く。
        アプリの一部ではないものに、アプリの package 規則を当てる意味がない。

        テストと同じモジュールを共有しているが、両者は別のことを書いている。
        定義は「どんな形をしているか」、テストは「どう振る舞うか」。区別はファイルの場所と名前でつく。
    """.trimIndent()
    example("ProjectArchitecture.kt", "定義の入口")
    example("roles/ScreenRole.kt", "Screen の役割の宣言")
    layout {
        ":architecture-test".module {
            testSourceSet / kotlin / "com/example/sample" {
                "ProjectArchitecture".ktFile()
                // The one shared helper this definition allows: the section headings every
                // group and role writes through, named exactly for the same reason
                // `ProjectArchitecture.kt` is.
                "DocumentSections".ktFile()
                // One declaration per file, and the file name says which kind it is:
                // `groups/` holds `*Group.kt` and `roles/` holds `*Role.kt`, so a helper
                // dropped into either is reported as `[UnexpectedFile]` rather than
                // quietly becoming a third kind of file.
                "groups" { "*Group".ktFile() }
                "roles" { "*Role".ktFile() }
            }
        }
    }
}
