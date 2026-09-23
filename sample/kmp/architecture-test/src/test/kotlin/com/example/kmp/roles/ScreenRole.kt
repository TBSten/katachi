package com.example.kmp.roles

import com.example.kmp.modulePackage
import me.tbsten.katachi.dsl.DeclarationContainerScope
import me.tbsten.katachi.dsl.gradle.*
import me.tbsten.katachi.dsl.kotlin.ktFile
import me.tbsten.katachi.dsl.pascalCase

/**
 * The role of one screen's UI: the `@Composable` its feature module is named after.
 *
 * `wildcards[0]` is what `:feature:*` matched — `home` for `:feature:home` — so the file
 * required of that module is `HomeScreen.kt` and nothing else: a `ProfileScreen.kt` under
 * `:feature:home` is an `[UnexpectedFile]`, and it is `[MissingFile]` that reports a feature
 * whose screen was renamed. Written as a plain `*Screen.kt` glob the check would have accepted
 * both.
 */
fun DeclarationContainerScope.screen() = "Screen" {
    title = "画面"
    summary = "1つの画面の @Composable。ViewModel の StateFlow を購読し、Component を組み合わせて描く"
    example("HomeScreen", "ホーム画面")
    example("SettingsScreen", "設定画面")
    layout {
        ":feature:*".module {
            "commonMain".sourceSet / kotlin / modulePackage /
                "${wildcards[0].pascalCase}Screen".ktFile()
        }
    }
}
