package com.example.kmp.roles

import me.tbsten.katachi.dsl.DeclarationContainerScope
import me.tbsten.katachi.dsl.gradle.*
import me.tbsten.katachi.dsl.kotlin.ktFile

/**
 * The `preview` package of `:ui`.
 *
 * `PreviewRoot` is the only thing in it, and every `@Preview` in the sample goes through it
 * instead of writing `AppTheme { }` itself. Kept apart from [preview], whose name it is a
 * prefix of, because the two are different things: one is the wrapper, the other is what the
 * wrapper is used by.
 */
fun DeclarationContainerScope.previewRoot() = "PreviewRoot" {
    title = "Preview root"
    summary = "The preview package of the :ui module. Wraps the content of a @Preview in AppTheme and Surface"
    description = """
        A package that holds only a wrapper for previews. `PreviewRoot` wraps the content in
        `AppTheme` and `Surface`: `AppTheme` gives the same colors as production and `Surface`
        paints their background behind it. Without it, every preview would copy the same two
        lines, and they would all drift apart the day the theme gains an argument.

        It is a different role from ui/Preview, whose name is similar. This one is the wrapping
        side and that one the wrapped side; no `@Preview` is written here. Conversely, nothing
        other than previews calls `PreviewRoot`.

        It sits in `commonMain`, not `commonTest`, because the `@Preview` that uses it lives in
        the `commonMain` of the feature modules. A test source set cannot be referenced from other
        modules, so it would not be reachable from there.

        This role is one of the few declarations that spell out the file name without a
        wildcard, so if `PreviewRoot.kt` is deleted or renamed it is reported as `[MissingFile]`.
    """.trimIndent()
    example("PreviewRoot", "The wrapper every @Preview uses")
    // One of the file names in this sample written without a wildcard, so it is also
    // one of the declarations that is reported as `[MissingFile]` when it disappears.
    layout {
        "ui" {
            "commonMain".sourceSet / kotlin / "com/example/kmp/ui" / "preview" / "PreviewRoot".ktFile()
        }
    }
}
