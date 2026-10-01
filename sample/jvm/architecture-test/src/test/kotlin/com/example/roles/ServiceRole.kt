@file:OptIn(ExperimentalKatachiApi::class)

package com.example.roles

import com.example.allowedContents
import com.example.forbiddenContents
import me.tbsten.katachi.ExperimentalKatachiApi
import me.tbsten.katachi.dsl.DeclarationContainerScope
import me.tbsten.katachi.dsl.LayoutScope
import me.tbsten.katachi.dsl.gradle.div
import me.tbsten.katachi.dsl.gradle.kotlin
import me.tbsten.katachi.dsl.gradle.mainSourceSet
import me.tbsten.katachi.dsl.kotlin.ktFile
import me.tbsten.katachi.dsl.template
import me.tbsten.katachi.konsist.konsist

/** The role holding one application-specific behaviour, built out of repositories. */
fun DeclarationContainerScope.service() = "Service" {
    title = "Service"
    summary = "Owns one application-specific behaviour, realized by combining Repositories"
    description = """
        The place that says what this app does. It is called from a Controller, combines the
        Repositories it needs, and returns a model. `HealthService.currentHealth()` for now just
        returns the result of `HealthRepository.load()`, but this is where decisions go when they
        multiply.

        The file name is `*Service.kt`, one class per file. Among the application's roles, only
        this one writes a constraint with `konsist { }`, "Must be public", so accidentally adding
        `internal` fails the test. You notice before it can no longer be referenced from a
        Controller in another package.
    """.trimIndent()
    allowedContents = """
        Only application-specific procedures, decisions and assembly may be placed here.
        Processing that spans several Repositories, or that cross-checks fetched values, comes
        here.
    """.trimIndent()
    forbiddenContents = """
        - Ktor types such as `Route`, `call` and `respond`. Knowledge of HTTP stops at the API
          layer
        - Data source details (connection targets, queries, file paths). The repository hides
          those
        - The definition of a value itself. A data class is the model's role
    """.trimIndent()
    example("HealthService", "Getting the server running status")
    // ./gradlew :architecture-test:katachiTemplate --arg template=domain.Service --arg name=User
    layout {
        mustBePublic()
        mainSourceSet / kotlin / "com/example/service" /
            "${capture("name")}Service".ktFile()
                .template {
                    val name = captureValue("name")
                    val kdoc by stringParameter(default = "Application-specific behaviour for $name.")
                    """
                        package com.example.service

                        /** $kdoc */
                        class ${name}Service {
                            fun execute(): String = TODO("Implement ${name}Service")
                        }
                    """.trimIndent() + "\n"
                }
    }
}

// Kept in this file rather than in a shared one: the declaration site is the first frame
// outside katachi, so a violation keeps naming the role that owns the rule.
//
// The samples' one `konsist { }` constraint on application code (katachi's guide: "Konsist integration"): the one
// line that shows a backend-written constraint next to katachi's own layout vocabulary. It covers only
// the `.kt` files this role declares, never the project's `build.gradle.kts`.
private fun LayoutScope.mustBePublic() =
    "Must be public".konsist {
        classes().must { it.hasPublicOrDefaultModifier }
    }
