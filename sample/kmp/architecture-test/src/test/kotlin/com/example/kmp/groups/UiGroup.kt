package com.example.kmp.groups

import com.example.kmp.forbiddenContents
import com.example.kmp.roles.component
import com.example.kmp.roles.navigation
import com.example.kmp.roles.preview
import com.example.kmp.roles.previewRoot
import com.example.kmp.roles.theme
import com.example.kmp.roles.uiCore
import me.tbsten.katachi.dsl.DeclarationContainerScope

/**
 * The shared UI modules a screen draws from: `:ui` and `:navigation`.
 *
 * The per-feature roles (`Screen` / `ViewModel` / `Route`) live in [featureGroup] instead. See
 * the note there for why the two are separate groups.
 *
 * Every layout here starts at a module path rather than at a directory name. `":ui".module { }`
 * is the directory the build puts `:ui` in, plus the two lines every Gradle module has
 * (`build/` is not checked, `build.gradle.kts` has to be there), and the package below it
 * comes from `modulePackage` instead of being spelled out per role.
 *
 * Component / Theme / UiCore all live in the one `:ui` module. They used to be
 * `:ui:component` / `:ui:theme` / `:ui:core`; now they are packages of `:ui`, which is the
 * shape katachi has to be able to describe.
 */
fun DeclarationContainerScope.uiGroup() = "ui".group {
    title = "UI"
    summary = ":ui and :navigation. Shared UI that belongs to no screen, and the definition of destinations"
    description = """
        Two shared modules that screens draw from. Whatever belongs to no particular screen
        gathers here.

        `:ui` is one module split into packages: `component` (parts), `theme` (appearance),
        `core` (the types for screen state) and `preview` (the base for previews), each one role.
        They used to be separate modules such as `:ui:component`, but were merged into packages.
        katachi needs to be able to say "this role is this package of this module", and it is
        also the shape more often seen in real projects.

        `:navigation` sits in the same group but does not depend on Compose. It holds only the
        list of destinations and the current location; how to show them is the job of `AppRoot`
        in `:app:android`. It is on the side that screens draw from, like `:ui`, so it is placed
        in this group.

        Every layout starts from a module path, and the package below it is derived from
        `modulePackage`. Instead of copying directory names, the policy of the whole
        sample is to write exactly what the build says.
    """.trimIndent()
    forbiddenContents = """
        - Per-screen Screen / ViewModel / Route. Those belong to the feature group
        - Dependencies on feature modules. Dependencies always point from a feature to `:ui` /
          `:navigation`; a single reference the other way makes the shared modules grow with every
          screen added
    """.trimIndent()

    component()
    theme()
    uiCore()
    preview()
    previewRoot()
    navigation()
}
