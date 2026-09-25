package com.example.groups

import me.tbsten.katachi.dsl.DeclarationContainerScope
import me.tbsten.katachi.dsl.gradle.gradle

/**
 * The `"Gradle"` group katachi ships (see [gradle]): the wrapper, `settings.gradle.kts`, every
 * module's `build.gradle.kts`, `gradle.properties` and the version catalog.
 *
 * This sample used to write that group out by hand as `build/Gradle`, one role with a `layout`
 * listing every file. `gradle()` says the same thing without writing the module list twice --
 * `":**".module { }` is the same wildcard `settings.gradle.kts` already answers -- and it splits
 * the one role into one per kind of file, the way this project's own convention already asks
 * roles to. There is no `"build".group { }` wrapping it any more: `gradle()`'s own `"Gradle"`
 * group already sets `documented = false` and a title, so a second group around it would only
 * repeat that.
 *
 * Because the group and every role inside it are declared inside katachi's own `GradleGroup.kt`,
 * not here, `Group.declaredAt` / `Role.declaredAt` for all of them resolve to the line below --
 * katachi walks the stack past its own frames to the first one outside itself, which is this
 * call, not the `"Gradle".group { }` katachi wrote. `ProjectArchitectureSpec` carves that whole
 * subtree out of the "one declaration, one file" checks it runs on the rest of the definition,
 * because there genuinely is only one declaration site here: this file.
 */
fun DeclarationContainerScope.gradleGroup() = gradle()
