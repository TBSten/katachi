package com.example.kmp.roles

import com.example.kmp.forbiddenContents
import com.example.kmp.modulePackage
import me.tbsten.katachi.dsl.DeclarationContainerScope
import me.tbsten.katachi.dsl.gradle.*
import me.tbsten.katachi.dsl.kotlin.ktFile

/** The `MaterialTheme` setup and the design tokens around it: the `theme` package of `:ui`. */
fun DeclarationContainerScope.theme() = "Theme" {
    title = "テーマ"
    summary = ":ui モジュールの theme package。MaterialTheme の設定と、色・余白のデザイントークン"
    description = """
        アプリの見た目の素を1か所にまとめた package です。`AppTheme` がアプリ唯一のテーマで、
        エントリポイント（`AppRoot`）と、すべてのプレビューが通る `PreviewRoot` がこれで包みます。
        テーマが2つに割れないよう、ここ以外で `MaterialTheme { }` を直接書きません。

        `AppSpacing` が `AppTheme` の隣にいるのは、Material 3 が余白のスキームを持たないからです。
        色は `ColorScheme` 経由で配れますが、余白は配る仕組みが無いので、部品が直接読む
        `object` として置いています。トークンを足すならこの package です。

        Android の `res/values/themes.xml` ではなく Kotlin 側にテーマを持っているのは KMP だからです。
        iOS には `res/` がないので、リソース XML に書いた見た目は共有できません。アプリ名のような
        Android ビルドが要求するものだけが `:app:android` の AndroidResource に残ります。
    """.trimIndent()
    forbiddenContents = """
        - 1つの画面だけで使う色や寸法。それはその画面の中の定数です
        - `@Composable` の部品。`component` package に置きます
    """.trimIndent()
    example("AppTheme", "アプリ全体のテーマ")
    example("AppSpacing", "余白のトークン")
    layout {
        ":ui".module {
            "commonMain".sourceSet / kotlin / modulePackage / "theme" / "*".ktFile()
        }
    }
}
