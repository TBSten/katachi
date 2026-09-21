package com.example

import com.example.application.apiRoles
import com.example.application.appRoles
import com.example.application.dataRoles
import com.example.application.domainRoles
import com.example.gradle.gradleRoles
import com.example.testing.testingRoles
import com.example.tool.toolRoles
import me.tbsten.katachi.dsl.Architecture
import me.tbsten.katachi.dsl.gradle.ModulePackage
import me.tbsten.katachi.dsl.architecture
import me.tbsten.katachi.dsl.gradle.capitalizedModuleNamePackage

/**
 * Where a module keeps its sources, below its own source set.
 *
 * `modulePackage` is not a katachi symbol: it is a `val` the project declares once and
 * writes into every `layout { }`, and it stands for a different directory in each module it
 * is read in. Here the application is the root project, so `":"` alone derives nothing and
 * the base package is the whole answer — `com/example`. A module named `:feature:debug-menu`
 * would derive `com/example/feature/debugMenu` from the same line.
 *
 * Declared next to [projectArchitecture] rather than inside a role file because every role
 * file uses it and none of them owns it.
 */
val modulePackage: ModulePackage = capitalizedModuleNamePackage("com.example")

/**
 * The architecture of this sample, described with katachi.
 *
 * The definition is split by meaning into one package per concern - `application`,
 * `testing`, `gradle`, `tool` - and each package exposes `ArchitectureScope` extension
 * functions. This file only calls them, so it stays short no matter how many roles the
 * project grows. katachi captures the declaration site of every group and role, so a
 * violation still points at the file the user actually wrote, not at this one.
 *
 * Every role carries a `layout { }` saying where its files may live, written in terms of
 * Gradle: `":".module { }` for the application, which is the root project itself,
 * `":architecture-test".module { }` for the module holding this definition, `mainSourceSet`
 * and `testSourceSet` for the source sets, and [modulePackage] for the package directory.
 * None of it is a new kind of declaration — `.module { }` is a directory plus the two lines
 * every Gradle module has, and a source set is the directory `src/<name>`.
 *
 * katachi denies by default, so this is an allow list: every file in the project has to be
 * covered by some role, and anything else fails `ProjectArchitectureTest`.
 */
val projectArchitecture: Architecture = architecture {
    apiRoles()
    domainRoles()
    dataRoles()
    appRoles()
    testingRoles()
    gradleRoles()
    toolRoles()
}
