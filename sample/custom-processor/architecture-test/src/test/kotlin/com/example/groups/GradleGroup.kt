package com.example.groups

import me.tbsten.katachi.dsl.DeclarationContainerScope
import me.tbsten.katachi.dsl.gradle.gradle

/**
 * The `"Gradle"` group katachi ships (see [gradle]): the wrapper, `settings.gradle.kts`, every
 * module's `build.gradle.kts`, the root `gradle.properties` and the version catalog.
 *
 * The group sets `documented = false` on itself and on every role inside it, so none of them
 * reaches the generated documentation, and `RoleDocCoverage` -- which walks up each role's
 * group path looking for that flag -- does not count them among the roles it checks.
 *
 * `gradle()` needs no module list: its build-script role covers the same
 * modules `settings.gradle.kts` already names, `:architecture-test` included.
 */
fun DeclarationContainerScope.gradleGroup() = gradle()
