@file:OptIn(ExperimentalKatachiApi::class)

package com.example.kmp.data

import com.example.kmp.data.actualImplementation
import com.example.kmp.data.expectDeclaration
import com.example.kmp.data.repositoryImplementation
import com.example.kmp.data.repositoryInterface
import com.example.kmp.forbiddenContents
import me.tbsten.katachi.ExperimentalKatachiApi
import me.tbsten.katachi.dsl.DeclarationContainerScope

fun DeclarationContainerScope.dataGroup() = "Data".group {
    title = "Data"
    summary = "The :data module. The way into data, and the parts whose implementation differs per platform"
    description = """
        One `:data` module split into packages. `user` holds the repositories (interface and
        implementation) and `platform` the expect/actual pair. Like `:ui`, it is split by package, not by module.

        All four roles sit in the same group because they are "a way to bring in values from outside
        the app". `UserRepository` is the way into data and `platformName()` the way into
        outside information about the runtime, and to the caller (the ViewModel) both look like a
        single dependency on `:data`.

        Confining KMP platform differences to this group is the design of this sample. Only
        `:data` has `androidMain` / `iosMain`; the UI side (`:ui` and the features) has no
        `expect`/`actual` at all. When something platform-specific is needed, push it down here
        instead of into a screen.
    """.trimIndent()
    forbiddenContents = """
        - UI types. `UiState` lives in the core package of `:ui`, and `:data` does not know about it
        - Test doubles. `FakeUserRepository` lives in `:testing`
        - `@Composable`. The `:data` build script does not apply the Compose plugin
    """.trimIndent()

    repositoryInterface()
    repositoryImplementation()
    expectDeclaration()
    actualImplementation()

    // The interface and its implementation as one set, in place of
    // `--arg template=Data.RepositoryInterface,Data.RepositoryImplementation`.
    //   ./gradlew :architecture-test:katachiTemplate \
    //       --arg template=Data.NewRepository --arg name=Profile
    "NewRepository".templateSet {
        title = "New repository"
        summary = "A repository's interface and its implementation"
        val name by stringParameter()

        template("Data.RepositoryInterface") {
            arg("name", name)
        }
        template("Data.RepositoryImplementation") {
            arg("name", name)
        }
    }
}
