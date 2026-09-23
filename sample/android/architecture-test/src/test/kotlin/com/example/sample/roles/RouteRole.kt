package com.example.sample.roles

import com.example.sample.groups.featureSources
import me.tbsten.katachi.dsl.DeclarationContainerScope
import me.tbsten.katachi.dsl.gradle.*
import me.tbsten.katachi.dsl.kotlin.ktFile
import me.tbsten.katachi.dsl.pascalCase

/** The role of a feature module's only public entry: where the rest of the app navigates to. */
fun DeclarationContainerScope.route() = "Route" {
    title = "Route"
    summary = "画面への遷移先。feature の外に公開する唯一の入口"
    example("HomeRoute", "ホーム画面への遷移先")
    layout {
        ":feature:*".module {
            featureSources() / "${wildcards[0].pascalCase}Route".ktFile()
        }
    }
}
