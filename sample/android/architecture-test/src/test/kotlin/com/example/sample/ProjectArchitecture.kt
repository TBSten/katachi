package com.example.sample

import com.example.sample.application.appRoles
import com.example.sample.application.dataRoles
import com.example.sample.application.featureRoles
import com.example.sample.application.uiRoles
import com.example.sample.gradle.gradleRoles
import com.example.sample.testing.testingRoles
import com.example.sample.tool.toolRoles
import me.tbsten.katachi.dsl.Architecture
import me.tbsten.katachi.dsl.architecture

/**
 * The architecture of this sample, written the way a user of katachi would write it.
 *
 * The declarations themselves sit in sibling packages, one per kind of concern, and each
 * exposes `ArchitectureScope` extension functions that this file only calls:
 *
 * - `application` — the app itself (`feature` / `ui` / `data` / `app`)
 * - `testing` — the shared fakes and the tests
 * - `gradle` — the build scripts. Named `gradle` and not `build` because `.gitignore`
 *   ignores `build/` at every level
 * - `tool` — everything else the repository carries, git for now
 *
 * Splitting a definition this way is what katachi recommends once it outgrows one file, so
 * the sample is also the worked example of it. Note that an extension function cannot be
 * called by its fully qualified name, which is why the imports above are needed.
 *
 * Step 1 only declares groups and roles: every `layout { }` is still empty, and filling
 * them in is what step 2 is for. Building this value reads nothing from disk, so it is
 * safe to hold in a top level `val`.
 *
 * [ProjectArchitectureSpec] asserts that every declaration's `declaredAt` points at the
 * file it is actually written in — not at this one — which is how the sample notices if
 * capturing the declaration site ever breaks in a real Android unit test run.
 */
val projectArchitecture: Architecture = architecture {
    featureRoles()
    uiRoles()
    dataRoles()
    appRoles()
    testingRoles()
    gradleRoles()
    toolRoles()
}
