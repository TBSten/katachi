package com.example.sample.roles

import me.tbsten.katachi.dsl.DeclarationContainerScope
import me.tbsten.katachi.dsl.gradle.*
import me.tbsten.katachi.dsl.kotlin.ktFile

/** The role of the types the UI layer itself is built out of. */
fun DeclarationContainerScope.uiCore() = "UiCore" {
    title = "UI foundation"
    summary = "Types that form the foundation of the UI layer, kept in the core package of the :ui module"
    description = """
        The vocabulary the UI layer uses to build itself. For now it is only `UiState`: a sealed
        interface saying whether the screen state is `Loading` / `Content` / `Error`, plus
        `contentOrNull`, which takes out the content of `Content`. The ViewModel emits it and
        the Screen branches on it with `when`.

        It is in `core`, not `component` or `theme`, because those two are written on top of
        this vocabulary. And it deliberately does not depend on Compose: a ViewModel in
        `:feature:*` can build a `UiState` without importing a single `androidx.compose.*`. If
        Compose types came in here, that line would disappear.

        File names are `*.kt`, and it is expected that types will increase. But only "UI
        vocabulary that several screens use in the same shape" may be added. The state of one
        screen (`HomeContent` / `SettingsContent`) goes in the same file as that feature's
        ViewModel. Rendering-related parts go in `component`, and colors and text in `theme`.
    """.trimIndent()
    example("UiState", "The sealed interface representing screen state")
    layout {
        "ui" {
            mainSourceSet / kotlin / "com/example/sample/ui" / "core" / "*".ktFile()
        }
    }
}
