package com.example.sample.roles

import com.example.sample.forbiddenContents
import me.tbsten.katachi.dsl.DeclarationContainerScope
import me.tbsten.katachi.dsl.gradle.*
import me.tbsten.katachi.dsl.kotlin.ktFile

/** The role of the one place colours, typography and shapes are decided. */
fun DeclarationContainerScope.theme() = "Theme" {
    title = "Theme"
    summary = "Colors, typography and shapes, kept in the theme package of the :ui module"
    description = """
        The one place that decides the foundation of the app's look. `AppTheme` wraps
        `MaterialTheme` with `lightColorScheme()` / `darkColorScheme()`, and the default of
        `darkTheme` is `isSystemInDarkTheme()`, so callers that write nothing follow the device
        setting.

        The `layout` names `AppTheme.kt` exactly instead of using a wildcard. There is only one
        theme, and a second file appearing here would mean "another theme" has quietly appeared,
        so the check fails on it. When adding colors too, rewrite `LightColorScheme` /
        `DarkColorScheme` inside `AppTheme.kt`.

        The app's entrypoint (`MainActivity`) and the preview base (`PreviewRoot`) wrap content
        in this, so shared components and feature Screens can read from
        `MaterialTheme.colorScheme` / `MaterialTheme.typography` without knowing which theme
        they are in.
    """.trimIndent()
    forbiddenContents = """
        - Colors or dimensions used by only one screen or one part. Write them where they are used
        - Background or `Surface` settings. The preview background is decided by `PreviewRoot`,
          and the real screen background by each Screen
    """.trimIndent()
    example("AppTheme", "The app theme")
    layout {
        "ui" {
            // Named exactly, not `*.kt`: there is one theme, and a second file turning up
            // here should be a violation rather than a silent second theme.
            mainSourceSet / kotlin / "com/example/sample/ui" / "theme" / "AppTheme".ktFile()
        }
    }
}
