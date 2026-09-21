package com.example.kmp

import com.example.kmp.application.appRoles
import com.example.kmp.application.dataRoles
import com.example.kmp.application.featureRoles
import com.example.kmp.application.uiRoles
import com.example.kmp.gradle.gradleRoles
import com.example.kmp.testing.testingRoles
import com.example.kmp.tool.toolRoles
import me.tbsten.katachi.dsl.Architecture
import me.tbsten.katachi.dsl.architecture

/**
 * The architecture of this sample, described with katachi.
 *
 * Step 1 only declares groups and roles; every `layout { }` is still empty. The paths each
 * role may occupy arrive in step 2, which is also when the definition starts to be checked
 * against the file system.
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
