@file:OptIn(ExperimentalKatachiApi::class)

package com.example.domain

import com.example.allowedContents
import com.example.data.repository
import com.example.forbiddenContents
import com.example.testing.test
import me.tbsten.katachi.ExperimentalKatachiApi
import me.tbsten.katachi.dsl.DeclarationContainerScope
import me.tbsten.katachi.dsl.gradle.kotlin
import me.tbsten.katachi.dsl.gradle.mainSourceSet
import me.tbsten.katachi.dsl.kotlin.ktFile
import me.tbsten.katachi.dsl.template
import me.tbsten.katachi.konsist.declarations.ktFileContent

fun DeclarationContainerScope.service() = "Service".role {
    title = "Service"
    summary = "Owns one application-specific behaviour, realized by combining Repositories"
    description = """
        The place that says what this app does. It is called from a Controller, combines the
        Repositories it needs, and returns a model. `HealthService.currentHealth()` for now just
        returns the result of `HealthRepository.load()`, but this is where decisions go when they
        multiply.

        The file name is `*Service.kt`, one class per file. The shape is written with
        `ktFileContent { }`, the way the file itself is written: the one class is public and named
        after the file, it holds nothing but the Repositories it receives and its functions, and
        the file imports models and Repositories only. Accidentally adding `internal` fails the
        test, so you notice before the class can no longer be referenced from a Controller in
        another package. An `io.ktor` import fails it too, which is how "knowledge of HTTP stops
        at the API layer" is held.
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
    // ./gradlew :architecture-test:katachiTemplate --arg template=Domain.Service --arg name=User
    layout {
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
    // What a Service file may hold, in the order the file lists it. Each kind is an allow list
    // of the lines written for it, and nothing else may be there: the package, the imports, the
    // one class and that class's members. The template above writes a file that passes it.
    "Must be public and import models and Repositories only, never HTTP".ktFileContent {
        `package`("com.example.service")

        // The domain knows the models and the Repositories, and nothing of HTTP.
        `import`("com.example.model.**")
        `import`("com.example.repository.**")

        required(public, `class`, fileName) {              // class HealthService; `internal` fails here
            allow(private, `val`, "*").type("*Repository")      // the Repositories it is built from
            allow(`fun`, "*")                              // the behaviour a Controller calls
        }
    }
}

