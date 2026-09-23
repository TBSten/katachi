package com.example.sample.roles

import me.tbsten.katachi.dsl.DeclarationContainerScope

/** The role of a `@Preview` function, which owns no file of its own. */
fun DeclarationContainerScope.preview() = "Preview" {
    title = "プレビュー"
    summary = "@Preview を付けた private @Composable。対象の Composable と同じファイルに置き、" +
        "中身は PreviewRoot で包む"
    example("AppButtonFilledPreview", "AppButton のプレビュー")
    example("HomeScreenContentPreview", "HomeScreen のプレビュー")
    // Deliberately empty. In this sample a preview is a function inside the file of
    // the Composable it previews, so it owns no path of its own: claiming one here
    // would duplicate what `Component` and `Screen` already allow.
    // TODO(step 4): state what this role really means with `konsist { }` — a
    //  `@Preview` function is private, is a @Composable, and wraps its body in
    //  `PreviewRoot`. Until then the role carries documentation only.
    layout { }
}
