package com.example.kmp.roles

import com.example.kmp.modulePackage
import me.tbsten.katachi.dsl.DeclarationContainerScope
import me.tbsten.katachi.dsl.gradle.*
import me.tbsten.katachi.dsl.kotlin.ktFile
import me.tbsten.katachi.dsl.pascalCase

/**
 * The state holder of one screen, named after the feature module it sits in.
 *
 * Tied to `wildcards[0]` the same way [screen] is: `:feature:home` may hold `HomeViewModel.kt`
 * and nothing else called `*ViewModel.kt`.
 */
fun DeclarationContainerScope.viewModel() = "ViewModel" {
    title = "ViewModel"
    summary = "画面の状態を持つ androidx.lifecycle.ViewModel。" +
        "Repository から取得した値を UiState に変換し、StateFlow で公開する"
    example("HomeViewModel", "ホーム画面の状態")
    layout {
        ":feature:*".module {
            "commonMain".sourceSet / kotlin / modulePackage /
                "${wildcards[0].pascalCase}ViewModel".ktFile()
        }
    }
}
