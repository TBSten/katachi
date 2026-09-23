package com.example.sample.roles

import com.example.sample.groups.featureSources
import me.tbsten.katachi.dsl.DeclarationContainerScope
import me.tbsten.katachi.dsl.gradle.*
import me.tbsten.katachi.dsl.kotlin.ktFile
import me.tbsten.katachi.dsl.pascalCase

/** The role holding one screen's state, named after the feature module it belongs to. */
fun DeclarationContainerScope.viewModel() = "ViewModel" {
    title = "ViewModel"
    summary = "画面の状態を StateFlow で公開し、イベントを受け取る androidx.lifecycle.ViewModel"
    example("HomeViewModel", "ホーム画面の状態")
    layout {
        ":feature:*".module {
            featureSources() / "${wildcards[0].pascalCase}ViewModel".ktFile()
        }
    }
}
