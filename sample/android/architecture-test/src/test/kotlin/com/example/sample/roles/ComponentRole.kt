package com.example.sample.roles

import com.example.sample.modulePackage
import me.tbsten.katachi.dsl.DeclarationContainerScope
import me.tbsten.katachi.dsl.gradle.div
import me.tbsten.katachi.dsl.gradle.kotlin
import me.tbsten.katachi.dsl.gradle.mainSourceSet
import me.tbsten.katachi.dsl.gradle.module
import me.tbsten.katachi.dsl.kotlin.ktFile

/** The role of a widget shared across features, rather than owned by one of them. */
fun DeclarationContainerScope.component() = "Component" {
    title = "共通コンポーネント"
    summary = ":ui モジュールの component package に置く、feature をまたいで使う部品"
    description = """
        複数の画面から呼ばれる Compose の部品。`AppButton` のように Material3 の上へ薄く被せて、
        形や強弱を変えたときに全画面へ一度に届くようにするために置く。

        見た目の選び分けは、この package の中の型で表す。`AppButton` は
        `emphasis: AppButtonEmphasis` を取り、`Filled` と `Outlined` の出し分けを内側でやる。
        呼び出し側が Material3 の `Button` と `OutlinedButton` を直接使い分けなくて済むようにするのが
        この役割の狙いなので、部品と、その部品のための enum や `@Preview` は同じファイルにまとめる。

        ファイル名は `*.kt` で縛っていない。部品は増えることが前提だから。ただし
        ここに置いてよいのは、2つ以上の feature が使うもの、あるいは使うと決まっているもの。
        1つの画面でしか使わない部品は、その feature モジュールに置く。

        置いてはいけないもの:

        - 状態の保持。値とコールバック（`text`、`onClick`）で受け渡し、`remember` で
          抱え込まない
        - `:data` や `:feature:*` への依存。`:ui` は下の層も横の feature も知らない
        - 色やタイポグラフィの直書き。`MaterialTheme` から読む（テーマ役割を参照）
    """.trimIndent()
    example("AppButton", "アプリ共通のボタン")
    layout {
        ":ui".module {
            mainSourceSet / kotlin / modulePackage / "component" / "*".ktFile()
        }
    }
    // `file()` names the file only: `component/` comes from the layout above. The preview is
    // wrapped in `PreviewRoot { }` from the start, as the Preview role asks.
    //   ./gradlew :architecture-test:katachiTemplate \
    //       --arg roleName=Component --arg name=Label
    template {
        val name by stringParameter()
        val previewText by stringParameter(default = name)
        val component = "App$name"

        file("$component.kt") {
            """
                package com.example.sample.ui.component

                import androidx.compose.material3.Text
                import androidx.compose.runtime.Composable
                import androidx.compose.ui.Modifier
                import androidx.compose.ui.tooling.preview.Preview
                import com.example.sample.ui.preview.PreviewRoot

                /** Shared ${name.lowercase()}, called by feature modules instead of Material's own. */
                @Composable
                fun $component(
                    text: String,
                    modifier: Modifier = Modifier,
                ) {
                    Text(text = text, modifier = modifier)
                }

                @Preview(showBackground = true)
                @Composable
                private fun ${component}Preview() = PreviewRoot {
                    $component(text = "$previewText")
                }
            """.trimIndent() + "\n"
        }
    }
}
