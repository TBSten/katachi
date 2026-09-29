package com.example.kmp.groups

import com.example.kmp.forbiddenContents
import com.example.kmp.roles.platformImplementation
import com.example.kmp.roles.repository
import me.tbsten.katachi.dsl.DeclarationContainerScope

/**
 * The data layer, including the parts that differ per platform.
 *
 * `:data` is one module split into packages, the same way `:ui` is: `user` holds the
 * repositories, `platform` the expect/actual pair.
 */
fun DeclarationContainerScope.dataGroup() = "data".group {
    title = "Data"
    summary = "The :data module. The way into data, and the parts whose implementation differs per platform"
    description = """
        One `:data` module split into packages. `user` holds the repositories and `platform` the
        expect/actual pair. Like `:ui`, it is split by package, not by module.

        Both roles sit in the same group because both are "a way to bring in values from outside
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

    repository()
    platformImplementation()
}
