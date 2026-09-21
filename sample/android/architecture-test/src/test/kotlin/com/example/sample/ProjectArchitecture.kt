package com.example.sample

import com.example.sample.application.appRoles
import com.example.sample.application.dataRoles
import com.example.sample.application.featureRoles
import com.example.sample.application.uiRoles
import com.example.sample.gradle.gradleRoles
import com.example.sample.testing.testingRoles
import com.example.sample.tool.toolRoles
import me.tbsten.katachi.dsl.Architecture
import me.tbsten.katachi.dsl.gradle.ModulePackage
import me.tbsten.katachi.dsl.architecture
import me.tbsten.katachi.dsl.gradle.capitalizedModuleNamePackage

/**
 * Where a module keeps its Kotlin sources, below its own source set.
 *
 * Declared once, here, and written as `modulePackage` inside every `module { }` block that
 * follows the convention: `:ui` means `com/example/sample/ui`, `:feature:home` means
 * `com/example/sample/feature/home`. It is a strategy rather than a string because one
 * `val` has to stand for a different directory in every module it is read in.
 *
 * Two modules of this sample do **not** follow it and write their package out as a plain
 * key instead: `:app`, whose sources sit directly in `com.example.sample` because it is the
 * application itself, and `:architecture-test`, which is not a layer of the app at all.
 * Bending the strategy into covering those two would hide, in a lambda, the very fact that
 * they are exceptions.
 */
val modulePackage: ModulePackage = capitalizedModuleNamePackage("com.example.sample")

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
 * Every `layout { }` below is written in terms of Gradle: a place is named by the module
 * path it belongs to (`":feature:*".module { }`), the source set inside it (`mainSourceSet`)
 * and [modulePackage], rather than by spelling the directories out. `:feature:*` is the one
 * to read first — it is matched against the modules that exist, and what the `*` captured is
 * read back as `wildcards[0]`, which is what ties a module's name to the names of the files
 * in it.
 *
 * Building this value reads nothing from disk — the `layout { }` blocks are deferred until
 * a check runs — so it is safe to hold in a top level `val`.
 *
 * [ProjectArchitectureTest] is the whole of what a user writes. [ProjectArchitectureSpec]
 * is katachi's own integration test on top of it, and asserts among other things that every
 * declaration's `declaredAt` points at the file it is actually written in — not at this one
 * — which is how the sample notices if capturing the declaration site ever breaks in a real
 * Android unit test run.
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
