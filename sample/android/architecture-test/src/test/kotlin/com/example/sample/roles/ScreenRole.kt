package com.example.sample.roles

import com.example.sample.groups.featureSources
import me.tbsten.katachi.dsl.DeclarationContainerScope
import me.tbsten.katachi.dsl.gradle.*
import me.tbsten.katachi.dsl.kotlin.ktFile
import me.tbsten.katachi.dsl.pascalCase

/**
 * The role of one screen's UI: the `@Composable` its feature module is named after.
 *
 * `wildcards[0]` is the module's own name, so a file is not merely allowed to be *a* screen
 * but has to be **that module's** screen: `:feature:home` may hold `HomeScreen.kt` and nothing
 * else called `*Screen.kt`.
 */
fun DeclarationContainerScope.screen() = "Screen" {
    title = "Screen"
    summary = "1つの画面の UI 実装となる @Composable。:feature:<name> ごとに <Name>Screen.kt を置く"
    example("HomeScreen", "ホーム画面")
    example("SettingsScreen", "設定画面")
    layout {
        ":feature:*".module {
            featureSources() / "${wildcards[0].pascalCase}Screen".ktFile()
        }
    }
}
