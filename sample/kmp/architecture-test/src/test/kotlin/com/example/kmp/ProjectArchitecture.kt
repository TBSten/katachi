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
 * Every role declares the paths it may occupy in its own `layout { }`, written as plain
 * directories and files: a module is a directory, a source set is a directory, a package is
 * a chain of directories. That is deliberately the most long-winded way to say it — step 3
 * replaces the repetition with `.module { }`, source sets and `modulePackage`, and the check
 * has to keep producing the same answer.
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
