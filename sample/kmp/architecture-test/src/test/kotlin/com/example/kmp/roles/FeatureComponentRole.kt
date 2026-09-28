package com.example.kmp.roles

import com.example.kmp.allowedContents
import com.example.kmp.forbiddenContents
import com.example.kmp.modulePackage
import me.tbsten.katachi.dsl.DeclarationContainerScope
import me.tbsten.katachi.dsl.gradle.*
import me.tbsten.katachi.dsl.kotlin.ktFile
import me.tbsten.katachi.dsl.pascalCase

/**
 * The role of a part one screen draws and no other: the feature-local counterpart of Component.
 *
 * This sample's example of generating into a wildcard module. `:feature:*` is named `feature`,
 * so `--arg feature=home` picks `:feature:home` as the one place the template writes to, and
 * the template reads the same value back with `captureValue("feature")` for the package and the
 * head of the file name. `:feature:home` holds one of them, `HomeUserCard.kt`, which the home
 * screen draws; `checkSampleKmp` generates another and checks it before deleting it again.
 */
fun DeclarationContainerScope.featureComponent() = "FeatureComponent" {
    title = "画面の部品"
    summary = "1つの画面でしか使わない @Composable。:feature:<name> の commonMain の component package に " +
        "<Name>*.kt で置く"
    description = """
        Screen が大きくなってきたときに切り出す、その画面専用の部品です。`:feature:home` なら
        `commonMain` の `component/HomeUserCard.kt` のように置き、ファイル名はモジュール名
        （`Home`）で始めます。1つの feature に何個あってもかまいません。

        共通コンポーネント（ui/Component）との違いは、使う画面の数です。2つ目の画面から
        使いたくなったら `:ui` の component package へ移します。feature 同士は互いに依存しないので、
        ここに置いたままでは他の画面からは呼べません。

        テンプレートから生成できます。`:feature:*` の `*` に `feature` と名前を付けてあるので、
        `--arg feature=home --arg name=UserCard` で `HomeUserCard.kt` が `:feature:home` に入ります。
        `feature` に渡せるのは実在する feature モジュールの名前だけです。
    """.trimIndent()
    allowedContents = """
        - 値とコールバックを受け取る `internal` な `@Composable`
        - `:ui` の Component / Theme を組み合わせる配置の記述
    """.trimIndent()
    forbiddenContents = """
        - ViewModel への依存。状態は Screen から値で受け取ります
        - `public` な宣言。feature の外から見えるのは Route だけです
    """.trimIndent()
    example("HomeUserCard", "ホーム画面だけで使う、ユーザー1人ぶんの表示")
    layout {
        ":feature:*".module(capture = "feature") {
            "commonMain".sourceSet / kotlin / modulePackage / "component" /
                "${wildcard("feature").pascalCase}*".ktFile()
        }
    }
    // The module is chosen by `--arg feature=...`, the name the layout gave `:feature:*`.
    // A module that does not exist is refused rather than created.
    //   ./gradlew :architecture-test:katachiTemplate \
    //       --arg roleName=FeatureComponent --arg feature=home --arg name=UserCard
    template {
        val feature = captureValue("feature")
        val name by stringParameter()
        val component = "${feature.pascalCase}$name"

        file("$component.kt") {
            """
                package com.example.kmp.feature.$feature.component

                import androidx.compose.material3.Text
                import androidx.compose.runtime.Composable
                import androidx.compose.ui.Modifier

                /** Part of the $feature screen that no other screen uses. */
                @Composable
                internal fun $component(
                    title: String,
                    modifier: Modifier = Modifier,
                ) {
                    Text(text = title, modifier = modifier)
                }
            """.trimIndent() + "\n"
        }
    }
}
