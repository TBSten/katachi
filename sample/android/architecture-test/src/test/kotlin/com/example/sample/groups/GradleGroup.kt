package com.example.sample.groups

import me.tbsten.katachi.dsl.DeclarationContainerScope
import me.tbsten.katachi.dsl.gradle.gradle

/**
 * The `"Gradle"` group katachi ships (see [gradle]): the wrapper, `settings.gradle.kts`, every
 * module's `build.gradle.kts`, the root `gradle.properties` and the version catalog.
 *
 * This used to be two hand-written roles — `build/GradleModule` for the per-module
 * `build.gradle.kts` files, `build/GradleRoot` for everything around them — under a
 * `"build".group { }` this sample wrote itself. `gradle()` says the same thing in one call,
 * split one kind of file per role instead of two roles split module-vs-root, and there is no
 * `"build"` group around it any more: `gradle()`'s own `"Gradle"` group already sets
 * `documented = false` and a title, so wrapping it in a second group would only repeat that.
 *
 * Because the group and every role inside it are declared inside katachi's own
 * `GradleGroup.kt`, not here, `Group.declaredAt` / `Role.declaredAt` for all of them resolve to
 * the line below — katachi walks the stack past its own frames to the first one outside
 * itself, which is this call. `ProjectArchitectureSpec` carves this subtree out of the "one
 * declaration, one file" checks it runs on the rest of the definition, because there genuinely
 * is only one declaration site here: this file.
 */
fun DeclarationContainerScope.gradleGroup() = gradle()
