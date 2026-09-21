package com.example

import com.example.application.apiRoles
import com.example.application.appRoles
import com.example.application.dataRoles
import com.example.application.domainRoles
import com.example.gradle.gradleRoles
import com.example.testing.testingRoles
import com.example.tool.toolRoles
import me.tbsten.katachi.dsl.Architecture
import me.tbsten.katachi.dsl.architecture

/**
 * The architecture of this sample, described with katachi.
 *
 * The definition is split by meaning into one package per concern - `application`,
 * `testing`, `gradle`, `tool` - and each package exposes `ArchitectureScope` extension
 * functions. This file only calls them, so it stays short no matter how many roles the
 * project grows. katachi captures the declaration site of every group and role, so a
 * violation still points at the file the user actually wrote, not at this one.
 *
 * Every role now carries a `layout { }` saying where its files may live, written with plain
 * directories and files. A module is spelled `"architecture-test" { ... }` and a source set
 * `"src" / "main"`, because step 2 deliberately brings no Gradle knowledge: `.module { }`,
 * `mainSourceSet` and `modulePackage` arrive in step 3, and the check has to give the same
 * answer once it is rewritten with them.
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
