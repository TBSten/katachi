package com.example.groups

import me.tbsten.katachi.dsl.DeclarationContainerScope
import me.tbsten.katachi.dsl.gradle.gradle

/**
 * The `"Gradle"` group katachi ships (see [gradle]): the wrapper, `settings.gradle.kts`, every
 * module's `build.gradle.kts`, the root `gradle.properties` and the version catalog.
 *
 * `gradle()` needs no module list: its build-script role covers the same
 * modules `settings.gradle.kts` already names. Its own `"Gradle"` group already sets
 * `documented = false` and a title, so it is called as is rather than wrapped in a group of
 * this sample's own.
 *
 * Because the group and every role inside it are declared inside katachi's own `GradleGroup.kt`,
 * not here, `Group.declaredAt` / `Role.declaredAt` for all of them resolve to the line below --
 * katachi walks the stack past its own frames to the first one outside itself, which is this
 * call, not the `"Gradle".group { }` katachi wrote. `ProjectArchitectureSpec` carves that whole
 * subtree out of the "one declaration, one file" checks it runs on the rest of the definition,
 * because there genuinely is only one declaration site here: this file.
 */
fun DeclarationContainerScope.gradleGroup() = gradle()
