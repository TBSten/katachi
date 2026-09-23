package com.example.kmp.groups

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

    component()
    theme()
    uiCore()
    preview()
    previewRoot()
    navigation()
}
