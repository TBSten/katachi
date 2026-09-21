package com.example.kmp

import com.example.kmp.application.appRoles
import com.example.kmp.application.dataRoles
import com.example.kmp.application.featureRoles
import com.example.kmp.application.uiRoles
import com.example.kmp.gradle.gradleRoles
import com.example.kmp.testing.testingRoles
import com.example.kmp.tool.toolRoles
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
 * declares it: `modulePackage` is not a built-in symbol of the DSL.
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
 * This file does nothing but call the declarations, which live in one package per concern:
 * `application` for the app itself, `testing` for test code, `gradle` for the build scripts
 * and `tool` for everything else. They are plain (non-inline) `ArchitectureScope` extension
 * functions, which is how katachi expects a definition to be split, and the declaration site
 * each declaration records is the file it is written in — `ProjectArchitectureSpec` proves
 * it.
 */
val projectArchitecture: Architecture = architecture {
    featureRoles()
    uiRoles()
    dataRoles()
    testingRoles()
    appRoles()
    gradleRoles()
    toolRoles()
}
