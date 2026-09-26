package com.example.roles

import com.example.forbiddenContents
import me.tbsten.katachi.dsl.DeclarationContainerScope
import me.tbsten.katachi.dsl.gradle.*
import me.tbsten.katachi.dsl.kotlin.ktFile

/** The role of the katachi definition itself, which belongs to no layer of the application. */
fun DeclarationContainerScope.architectureDefinition() = "ArchitectureDefinition" {
    title = "アーキテクチャ定義"
    summary = "katachi の DSL で書かれた役割の定義と、それを assert するテスト"
    description = """
        このプロジェクトの形を書いたコードそのものです。アプリのどのレイヤーにも属さないので、
        `:architecture-test` という専用モジュールに置きます。

        中身は1宣言1ファイルです。`ProjectArchitecture.kt` が入口、`groups/<Name>Group.kt` が
        グループ、`roles/<Name>Role.kt` が役割です。全 group・全役割が `by` で使う節の定義は
        `DocumentSections.kt` にまとめてあり、`ProjectArchitecture.kt` と同じく名指しで許して
        あります。定義どおりかを確かめる `ProjectArchitectureTest`、3本の processor を API から
        呼ぶ `CustomProcessorSpec`、katachi 自身の番兵である `LayoutSnapshotSpec` も同じ
        モジュールにあり、この役割が覆います。

        `layout { }` はパッケージを `"com/example"` と直に書いています。`modulePackage` を
        使わないのは、このモジュールのソースがモジュール名から導かれる
        `com/example/architectureTest` ではなく `com/example` に置かれているからです。
    """.trimIndent()
    forbiddenContents = """
        - processor 本体。`processors/` は「プロセッサ」の役割の担当で、わざと分けてあります。
          定義は形を書くもの、processor はその形を読んで何かを作るもので、読む向きが逆だからです。
          分けておくと `RoleFileCount` の出力にも2つが別の行として出ます
        - アプリのコード。`:architecture-test` に `src/main/kotlin` を作ると、
          どの役割も覆わないファイルとして落ちます
    """.trimIndent()
    example("ProjectArchitecture.kt", "定義の入口")
    example("roles/StoreRole.kt", "役割1つの宣言")
    example("ProjectArchitectureTest.kt", "定義を assert するテスト")
    layout {
        // The price of the recommended setup: `:architecture-test` checks itself, so the
        // definition has to give itself a role like everything else. `processors/` is
        // deliberately not listed here -- the `Processor` role claims it.
        ":architecture-test".module {
            testSourceSet / kotlin / "com/example" {
                "ProjectArchitecture".ktFile()
                "DocumentSections".ktFile()
                "ProjectArchitectureTest".ktFile()
                "*Spec".ktFile()
                "groups" / "*".ktFile()
                "roles" / "*".ktFile()
            }
        }
    }
}
