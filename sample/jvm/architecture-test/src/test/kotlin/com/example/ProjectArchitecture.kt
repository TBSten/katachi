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
 * Step 1 declares groups and roles only. Every `layout { }` is still an empty block: the
 * path DSL that says *where* each role's files live arrives in step 2, and the blocks are
 * stored unevaluated until then. An empty block is kept on each role on purpose, so that
 * step 2 only has to fill them in.
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
