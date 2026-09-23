package com.example.kmp.roles

import com.example.kmp.modulePackage
import me.tbsten.katachi.dsl.DeclarationContainerScope
import me.tbsten.katachi.dsl.gradle.*
import me.tbsten.katachi.dsl.kotlin.ktFile
import me.tbsten.katachi.dsl.pascalCase

/**
 * What binds a screen to a navigation destination, named after its feature module.
 *
 * The third of the three files a feature module is required to hold, and the third place
 * `wildcards[0]` ties a file name to the module it sits in.
 */
fun DeclarationContainerScope.route() = "Route" {
    title = "ルート"
    summary = "画面を navigation の Destination に結びつけ、ViewModel の生成も引き受ける"
    example("HomeRoute", "ホーム画面の遷移先")
    layout {
        ":feature:*".module {
            "commonMain".sourceSet / kotlin / modulePackage /
                "${wildcards[0].pascalCase}Route".ktFile()
        }
    }
}
