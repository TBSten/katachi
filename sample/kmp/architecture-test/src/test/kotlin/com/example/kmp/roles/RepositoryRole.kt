@file:OptIn(ExperimentalKatachiApi::class)

package com.example.kmp.roles

import com.example.kmp.forbiddenContents
import com.example.kmp.modulePackage
import me.tbsten.katachi.ExperimentalKatachiApi
import me.tbsten.katachi.dsl.DeclarationContainerScope
import me.tbsten.katachi.dsl.gradle.div
import me.tbsten.katachi.dsl.gradle.kotlin
import me.tbsten.katachi.dsl.gradle.module
import me.tbsten.katachi.dsl.gradle.sourceSet
import me.tbsten.katachi.dsl.kotlin.ktFile
import me.tbsten.katachi.dsl.template

/**
 * The way into the data of the app: the `user` package of `:data`.
 *
 * There is no `settings` package beside it. Unlike sample/android this sample has no
 * SettingsRepository, and the settings screen reads `:data` through UserRepository and
 * `platformName()`.
 */
fun DeclarationContainerScope.repository() = "Repository" {
    title = "Repository"
    summary = "The :data module's user package. The way into data, placed as an interface and an implementation"
    description = """
        The entry through which the app touches data. In the `user` package of `:data`, place
        the interface (`*Repository.kt`) and the implementation (`*RepositoryImpl.kt`) side by
        side. The caller (the ViewModel) depends only on the interface.

        The layout is declared in two parts because the name of an interface does not match the
        file name of an implementation. A partial-match capture, like `*`, does not cross the
        string that follows it, so picking up the implementation needs the implementation's own
        pattern. Trying to loosen a single pattern to cover both would let through shapes that
        were never declared.

        The file declarations of the interface and the implementation each carry one `.template`. Running
        `--arg template=data.Repository.repository,data.Repository.repositoryImpl --arg name=Cache`
        creates both at once, while specifying only `data.Repository.repository` creates
        only the interface (write the implementation by hand, or add it later with
        `data.Repository.repositoryImpl`).

        Only `commonMain` is declared. What changes its implementation per platform is taken on
        by the neighboring PlatformImplementation (expect/actual), not by this role. Adding
        `androidMain` here would scatter the same "absorbing platform differences" over two places.

        Unlike sample/android, this sample has no repository for settings. The settings screen
        is served by just reading `UserRepository` and `platformName()`. Writing an unused
        package in the definition would make the documentation point at a directory that does not exist.
    """.trimIndent()
    forbiddenContents = """
        - UI types. `UiState` lives in the core package of `:ui`, and `:data` does not know about it
        - Fake implementations for tests. `FakeUserRepository` belongs to the Fake role of `:testing`
    """.trimIndent()
    example("UserRepository", "The interface that fetches users")
    example("UserRepositoryImpl", "The implementation of UserRepository")
    // Two file declarations, not one: an interface file name does not match an implementation
    // file name, because a capture never crosses what follows it, same as `*`.
    // Each carries its own `.template`, told apart by `id` (`repository` / `repositoryImpl`).
    //   ./gradlew :architecture-test:katachiTemplate --arg template=data.Repository.repository \
    //       --arg name=Cache
    layout {
        ":data".module {
            "commonMain".sourceSet / kotlin / modulePackage / "user" {
                "${capture("name")}Repository".ktFile()
                    .template(id = "repository") {
                        val name = captureValue("name")
                        val item by stringParameter(default = "String")
                        """
                            package com.example.kmp.data.user

                            /** Reads ${name.lowercase()} data. */
                            interface ${name}Repository {
                                fun items(): List<$item>
                            }
                        """.trimIndent() + "\n"
                    }
                "${capture("name")}RepositoryImpl".ktFile()
                    .template(id = "repositoryImpl") {
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
