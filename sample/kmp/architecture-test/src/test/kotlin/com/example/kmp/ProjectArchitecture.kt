package com.example.kmp

import com.example.kmp.groups.appGroup
import com.example.kmp.groups.buildGroup
import com.example.kmp.groups.dataGroup
import com.example.kmp.groups.featureGroup
import com.example.kmp.groups.testingGroup
import com.example.kmp.groups.toolGroup
import com.example.kmp.groups.uiGroup
import me.tbsten.katachi.dsl.Architecture
import me.tbsten.katachi.dsl.gradle.ModulePackage
import me.tbsten.katachi.dsl.architecture
import me.tbsten.katachi.dsl.gradle.capitalizedModuleNamePackage

/**
 * Where a module keeps its Kotlin package, as a derivation rather than as a path.
 *
 * Every module of this sample puts its sources under `com.example.kmp.<module path>`, so one
 * strategy covers them all: `:data` is `com/example/kmp/data` and `:feature:home` is
 * `com/example/kmp/feature/home`. Inside a `.module { }` block the value stands for whichever
 * module is being evaluated, which is why it cannot be a string.
 *
 * Two modules are deliberately *not* written with it, because their package does not follow
 * their module path: `:app:android` holds `com.example.kmp.app`, and `:architecture-test`
 * holds `com.example.kmp` itself. Those two spell the package out, which is the honest way to
 * say "this one is different".
 *
 * Declared as a top level `val` next to [projectArchitecture], the way a user of katachi
 * declares it: `modulePackage` is not a built-in symbol of the DSL. It sits here rather than in
 * one role's file because every group reads it and none of them owns it.
 */
val modulePackage: ModulePackage = capitalizedModuleNamePackage("com.example.kmp")

/**
 * The architecture of this sample, described with katachi.
 *
 * Every role declares the paths it may occupy in its own `layout { }`. A Gradle module is
 * written as its module path with `.module { }`, a source set as `"commonMain".sourceSet`,
 * and the package below it as [modulePackage], so that the definition says what the build
 * says instead of repeating the directory names the build happens to use.
 *
 * `files` is not set, so the default `gitTracked()` applies, and it does the work: `build/`,
 * `.kotlin/` and `local.properties` are never offered to the check, so no role has to claim
 * them. This sample is a directory of the katachi repository rather than a repository of its
 * own — its project root is the directory holding its own `gradlew`, and the `.git` is two
 * levels above — but that changes nothing, because katachi asks git whether the root is
 * inside a work tree rather than looking for a `.git` beside it.
 *
 * Kept in a top level `val`: building it reads nothing and runs no check, so the same value
 * can be shared by every test and, later, by documentation generation.
 *
 * This file does nothing but call the seven group functions. The definition itself is split
 * one declaration per file: a role named `"UiCore"` is declared in the `roles` package, in
 * `UiCoreRole.kt`, and a group named `"build"` in the `groups` package, in `BuildGroup.kt`.
 * The file name is the whole of the convention, so nothing has to be written down twice — and
 * a `build` *directory* would have been invisible to git, because `.gitignore` ignores `build/`
 * at every level, but that entry matches directories only, so a file named after the group is
 * safe.
 *
 * Both kinds are plain `DeclarationContainerScope` extension functions — the scope
 * `architecture { }` and `"...".group { }` share — so a role can be moved into another group
 * without its own file being touched.
 *
 * None of them may be `inline`. An inlined frame reports the caller's file with a line number
 * remapped past the end of that file, and katachi captures the declaration site from the
 * stack, so every declaration would record a position nobody wrote. Written once here rather
 * than repeated in twenty-seven files; `ProjectArchitectureSpec` is what actually holds the
 * line, by reading the captured line back out of the source.
 */
val projectArchitecture: Architecture = architecture {
    featureGroup()
    uiGroup()
    dataGroup()
    testingGroup()
    appGroup()
    buildGroup()
    toolGroup()
}
