package com.example.sample.roles

import com.example.sample.modulePackage
import me.tbsten.katachi.dsl.DeclarationContainerScope
import me.tbsten.katachi.dsl.gradle.*
import me.tbsten.katachi.dsl.kotlin.ktFile

/** The role of moving between screens, which belongs to no single feature. */
fun DeclarationContainerScope.navigation() = "Navigation" {
    title = "画面遷移"
    summary = ":navigation に置く、画面間の移動"
    example("AppNavigator", "画面遷移の窓口")
    layout {
        ":navigation".module {
            mainSourceSet / kotlin / modulePackage / "*".ktFile()
        }
    }
}
