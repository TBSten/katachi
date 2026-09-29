package com.example.sample.roles

import me.tbsten.katachi.dsl.DeclarationContainerScope

/** The role of a `@Preview` function, which owns no file of its own. */
fun DeclarationContainerScope.preview() = "Preview" {
    title = "Preview"
    summary = "A private @Composable annotated with @Preview. Placed in the same file as its target Composable, with the body wrapped in PreviewRoot"
    description = """
        A `private` `@Composable` that exists only to appear in Android Studio's preview. It
        goes at the end of the **same file** as the target Composable, and exists both in `:ui`
        shared components and in `:feature:*` Screens. That is why only this role has an empty
        `layout { }`: it owns no file of its own. Where it goes is already allowed by the shared
        component role and the Screen role, and writing it again here would declare the same
        place twice.

        The body is always wrapped in `PreviewRoot { }` from the `preview` package of `:ui`. If
        each preview wrote `AppTheme { }` directly, whether a background is laid down and how
        the theme is passed would drift from preview to preview (see the preview base role).

        Pass only state. `HomeScreenContentPreview` passes `UiState.Content(...)` and
        `HomeScreenLoadingPreview` passes `UiState.Loading`, lining up different states of the
        same screen. The overload that takes `viewModel()` is not previewed. To show light and
        dark side by side, use `PreviewRoot(darkTheme = true)` (`AppButtonFilledDarkPreview`).

        This convention is not checked in this sample. "Is private", "is a `@Composable`" and
        "is wrapped in `PreviewRoot`" cannot be expressed by where a file sits, and writing them
        would take `konsist { }`. Since this role owns no file, the constraint would go on the
        shared component role and the Screen role that hold the previews. This sample does not
        go that far and keeps it a prose-only role (for an example of `konsist { }`, see
        sample/jvm).
    """.trimIndent()
    example("AppButtonFilledPreview", "The preview of AppButton")
    example("HomeScreenContentPreview", "The preview of HomeScreen")
    // Deliberately empty. In this sample a preview is a function inside the file of
    // the Composable it previews, so it owns no path of its own: claiming one here
    // would duplicate what `Component` and `Screen` already allow.
    // What it means -- private, @Composable, body wrapped in `PreviewRoot` -- is left as
    // prose. A `konsist { }` constraint could say it, but it would have to sit on
    // `Component` and `Screen`, the roles that own the files; sample/jvm is the sample that
    // shows `konsist { }`.
    layout { }
}
