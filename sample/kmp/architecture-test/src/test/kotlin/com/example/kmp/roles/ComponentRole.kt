package com.example.kmp.roles

import com.example.kmp.modulePackage
import me.tbsten.katachi.dsl.DeclarationContainerScope
import me.tbsten.katachi.dsl.gradle.*
import me.tbsten.katachi.dsl.kotlin.ktFile

/** The shared `@Composable` parts several screens draw with: the `component` package of `:ui`. */
fun DeclarationContainerScope.component() = "Component" {
    title = "共通コンポーネント"
    summary = ":ui モジュールの component package。複数の画面から使われる @Composable 部品"
    description = """
        複数の画面から使われる `@Composable` の部品です。`:ui` モジュールの `component` package に
        置きます。`:ui` は Compose Multiplatform のモジュールで `commonMain` しか持たないので、
        Android と iOS のどちらからも同じ部品が使われます。

        置いてよいもの:

        - 画面を知らない部品。`label` や `onClick` のような素の引数だけを受け取り、状態は持ちません
        - `theme` package のトークン（`AppSpacing` など）を読むこと

        置いてはいけないもの:

        - 1つの画面でしか使わない部品。その feature モジュールの Screen に `internal` で書きます
        - `UiState` や ViewModel を引数に取る部品。`:ui` は feature 側を知らない側なので、
          ここが画面の状態を知ると依存が逆流します
        - `androidMain` 向けの実装。Android 専用の View が要る話になったら、それは
          `:data` の PlatformImplementation と同じく expect/actual で解く問題です

        部品が増えたらまずここに1ファイル足します。プレビューは同じ package の
        `<部品名>Preview.kt`（ui/Preview の役割）に分けて書くので、この役割のファイルに
        `@Preview` は入りません。`component/*.kt` は `*Preview.kt` にも一致するため、
        katachi は重なりを `[AmbiguousLayout]` として報告します。これは承知の上の形です。
    """.trimIndent()
    example("PrimaryButton", "主要な操作のボタン")
    layout {
        ":ui".module {
            "commonMain".sourceSet / kotlin / modulePackage / "component" / "*".ktFile()
        }
    }
}
