package com.example.roles

import com.example.forbiddenContents
import me.tbsten.katachi.dsl.DeclarationContainerScope
import me.tbsten.katachi.dsl.gradle.*
import me.tbsten.katachi.dsl.kotlin.ktFile

/** The role of katachi's own integration tests, which a project merely using katachi does not need. */
fun DeclarationContainerScope.integrationSpec() = "IntegrationSpec" {
    title = "Integration spec"
    summary = "katachi's own integration tests, run against this real project"
    description = """
        The `*Spec` files check katachi itself in a real user build: that the definition is
        modelled as written, that a role's declaration site is the line that wrote it, that the
        layout snapshot is up to date. They are here because `:katachi` compiles with settings a
        user's build does not have. A project that merely uses katachi does not write them.
    """.trimIndent()
    forbiddenContents = """
        - The test a project adopting katachi writes. That is `ProjectArchitectureTest`
    """.trimIndent()
    example("ProjectArchitectureSpec.kt", "Checks the definition is modelled as written")
    layout {
        "architecture-test" / testSourceSet / kotlin / "com/example" / "*Spec".ktFile()
    }
}
