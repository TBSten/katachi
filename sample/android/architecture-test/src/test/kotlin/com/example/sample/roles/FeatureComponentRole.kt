package com.example.sample.roles

import com.example.sample.allowedContents
import com.example.sample.forbiddenContents
import com.example.sample.groups.FeatureModule
import com.example.sample.groups.eachFeatureModule
import com.example.sample.groups.featureSources
import me.tbsten.katachi.dsl.DeclarationContainerScope
import me.tbsten.katachi.dsl.gradle.div
import me.tbsten.katachi.dsl.kotlin.ktFile

/**
 * The role of a widget one screen uses and no other: the feature-local counterpart of Component.
 *
 * Its layout names every feature module through [FeatureModule] rather than `":feature:*"`, and
 * a file name has to start with that module's name. Both are what lets the template below pick
 * its module: `HomeUserCard.kt` fits only `:feature:home`'s pattern, so katachi has one place
 * to put it.
 */
fun DeclarationContainerScope.featureComponent() = "FeatureComponent" {
    title = "画面の部品"
    summary = "1つの画面でしか使わない @Composable。:feature:<name> の component package に " +
        "<Name>*.kt で置く"
    description = """
        Screen が大きくなってきたときに切り出す、その画面専用の部品。`:feature:home` なら
        `component/HomeUserCard.kt` のように、feature モジュールの `component` package に置き、
        ファイル名はモジュール名（`Home`）で始める。1つの feature に何個あってもよい。

        共通コンポーネント役割との違いは、使う画面の数。2つ目の feature から呼びたくなったら
        `:ui` の component package へ移して `App*` にする。feature 同士は互いに依存しないので、
        ここに置いたままでは他の feature からは呼べない。

        ファイル名の頭をモジュール名にそろえるのは、テンプレートが生成先を選べるようにするため。
        `--arg feature=Home --arg name=UserCard` で `HomeUserCard.kt` が `:feature:home` に入る。
    """.trimIndent()
    allowedContents = """
        - 値とコールバックを受け取る `internal` な `@Composable`
        - その部品の `@Preview`（`PreviewRoot { }` で包む。プレビュー役割を参照）
    """.trimIndent()
    forbiddenContents = """
        - ViewModel への依存。状態は Screen から値で受け取る
        - 他の feature の型、`public` な宣言。外から見えるのは Route だけ
    """.trimIndent()
    example("HomeUserCard", "ホーム画面だけで使うカード（例）")
    layout {
        eachFeatureModule { feature ->
            featureSources() / "component" / "${feature.name}*".ktFile()
        }
    }
    // The module is chosen by the file name: `${feature.name}$name.kt` fits only that
    // module's `component/<Feature>*.kt`.
    //   ./gradlew :architecture-test:katachiTemplate \
    //       --arg roleName=FeatureComponent --arg feature=Home --arg name=UserCard
    template {
        val feature by enumParameter(FeatureModule.entries)
        val name by stringParameter()
        val withPreview by booleanParameter(default = true)
        val component = "${feature.name}$name"
        val previewImports = if (withPreview) {
            """
                import androidx.compose.ui.tooling.preview.Preview
                import com.example.sample.ui.preview.PreviewRoot

            """.trimIndent()
        } else {
            ""
        }
        val preview = if (withPreview) {
            "\n\n" + """
                @Preview(showBackground = true)
                @Composable
                private fun ${component}Preview() = PreviewRoot {
                    $component(title = "$name")
                }
            """.trimIndent()
        } else {
            ""
        }

        file("$component.kt") {
            """
                |package com.example.sample.feature.${feature.name.lowercase()}.component
                |
                |import androidx.compose.foundation.layout.Arrangement
                |import androidx.compose.foundation.layout.Column
                |import androidx.compose.material3.MaterialTheme
                |import androidx.compose.material3.Text
                |import androidx.compose.runtime.Composable
                |import androidx.compose.ui.Modifier
                |import androidx.compose.ui.unit.dp
                |$previewImports
                |/** Part of the ${feature.name.lowercase()} screen that no other screen uses. */
                |@Composable
                |internal fun $component(
                |    title: String,
                |    modifier: Modifier = Modifier,
                |) {
                |    Column(
                |        modifier = modifier,
                |        verticalArrangement = Arrangement.spacedBy(8.dp),
                |    ) {
                |        Text(text = title, style = MaterialTheme.typography.titleMedium)
                |    }
                |}$preview
            """.trimMargin()
        }
    }
}
