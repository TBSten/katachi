package com.example.sample.roles

import me.tbsten.katachi.dsl.DeclarationContainerScope
import me.tbsten.katachi.dsl.gradle.*

/** The role of the shrinker rules of the app. */
fun DeclarationContainerScope.proguardRules() = "ProguardRules" {
    title = "Proguard rules"
    summary = "proguard-rules.pro of :app, the rules the code shrinker reads"
    description = """
        The `proguard-rules.pro` at the root of `:app`. Its shape is decided by the Android
        build system, not by this project. There is only one per app, so it is named exactly
        and a second one is a violation.
    """.trimIndent()
    example("proguard-rules.pro", "The rules the shrinker reads")
    layout {
        ":app".module {
            "proguard-rules.pro".file()
        }
    }
}
