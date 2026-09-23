package com.example

import com.example.groups.apiGroup
import com.example.groups.appGroup
import com.example.groups.buildGroup
import com.example.groups.dataGroup
import com.example.groups.domainGroup
import com.example.groups.testingGroup
import com.example.groups.toolGroup
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
 * The definition is split one declaration per file: `roles/<Name>Role.kt` holds one role and
 * `groups/<Name>Group.kt` holds one group, which says what it is made of by calling the role
 * functions in order. Both are extensions on `DeclarationContainerScope` - the scope that
 * `architecture { }` and `"...".group { }` share - so a role can be moved into another group
 * without touching the role's own file. This file only calls the seven group functions, so it
 * stays short no matter how many roles the project grows.
 *
 * None of those functions may be `inline`. An inlined frame reports the caller's file with a
 * line number past its end, and katachi captures the declaration site from the stack, so the
 * violation would point at a line nobody wrote. Written once here rather than repeated in
 * eighteen files; `ProjectArchitectureSpec` is what actually holds the line.
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
    apiGroup()
    domainGroup()
    dataGroup()
    appGroup()
    testingGroup()
    buildGroup()
    toolGroup()
}
