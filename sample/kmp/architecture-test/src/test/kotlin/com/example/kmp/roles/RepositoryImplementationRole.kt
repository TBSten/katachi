@file:OptIn(ExperimentalKatachiApi::class)

package com.example.kmp.roles

import com.example.kmp.forbiddenContents
import me.tbsten.katachi.ExperimentalKatachiApi
import me.tbsten.katachi.dsl.DeclarationContainerScope
import me.tbsten.katachi.dsl.gradle.div
import me.tbsten.katachi.dsl.gradle.kotlin
import me.tbsten.katachi.dsl.gradle.sourceSet
import me.tbsten.katachi.dsl.kotlin.ktFile
import me.tbsten.katachi.dsl.template

/** The implementations of the interfaces in [repositoryInterface], beside them in the `user` package. */
fun DeclarationContainerScope.repositoryImplementation() = "RepositoryImplementation" {
    title = "Repository implementation"
    summary = "The :data module's user package. The implementation of a repository interface"
    description = """
        The implementation of a RepositoryInterface. It goes in the `user` package of `:data`
        as `*RepositoryImpl.kt`, beside the interface it implements. `MainActivity` creates it
        and hands it to `AppRoot`; every other caller sees the interface.

        It is a role of its own, not a second layout of RepositoryInterface, because the two are
        different kinds of file. `*RepositoryImpl.kt` is also a name the interface pattern
        `*Repository.kt` cannot match, so a file in the wrong place is reported as a violation
        instead of quietly passing.

        The file declaration carries one `.template` whose `name` capture is shared with the
        interface's template: running
        `--arg template=data.RepositoryInterface,data.RepositoryImplementation --arg name=Cache`
        creates both at once, and the `item` parameter binds both when given once.
    """.trimIndent()
    forbiddenContents = """
        - Fake implementations for tests. `FakeUserRepository` belongs to the Fake role of `:testing`
        - UI types. `UiState` lives in the core package of `:ui`, and `:data` does not know about it
    """.trimIndent()
    example("UserRepositoryImpl", "The implementation of UserRepository")
    layout {
        "data" {
            "commonMain".sourceSet / kotlin / "com/example/kmp/data" / "user" {
                "${capture("name")}RepositoryImpl".ktFile()
                    .template {
                        val name = captureValue("name")
                        // Same parameter as the interface's template: give it once and it binds both,
                        // when the two are generated together.
                        val item by stringParameter(default = "String")
                        """
                            package com.example.kmp.data.user

                            /** The real implementation. A stub, like [UserRepositoryImpl]. */
                            class ${name}RepositoryImpl : ${name}Repository {
                                override fun items(): List<$item> = emptyList()
                            }
                        """.trimIndent() + "\n"
                    }
            }
        }
    }
}
