package me.tbsten.katachi.test.architecture.groups

import me.tbsten.katachi.dsl.DeclarationContainerScope
import me.tbsten.katachi.dsl.gradle.gradle
import me.tbsten.katachi.test.architecture.roles.buildLogic

/**
 * The `"Gradle"` group katachi ships (see [gradle]): the wrapper, `settings.gradle.kts`, every
 * module's `build.gradle.kts`, `gradle.properties` and the version catalog.
 *
 * This used to be `"build".group { }`, holding two hand-written roles -- `GradleRoot` for the
 * files around the root project, `GradleModule` for the per-module `build.gradle.kts` the same
 * `.module { }` wildcard `settings.gradle.kts` already answers -- plus `BuildLogic` for
 * `buildSrc`. `gradle()` says the first two in one call and splits them one kind of file per
 * role instead of root-vs-module. There is no `"build".group { }` wrapping it any more:
 * `gradle()`'s own `"Gradle"` group already sets `documented = false` and a title, so a second
 * group around it would only repeat that.
 *
 * `BuildLogic` stays, added inside [gradle]'s `block`, exactly as its own KDoc's example shows
 * adding a role of the project's own. `buildSrc` is not a module `":**".module { }` ever
 * reaches -- module discovery never walks into it -- so `gradle()` cannot cover it itself, and
 * it is still a build concern rather than a `tool` one. Adding it here rather than reopening
 * `"build".group { }` keeps the same one-call shape the rest of this group has.
 *
 * Because the group and its own five roles are declared inside katachi's own `GradleGroup.kt`,
 * not here, `Group.declaredAt` / `Role.declaredAt` for all of them resolve to the line below --
 * katachi walks the stack past its own frames to the first one outside itself, which is this
 * call. `BuildLogic` is different: `buildLogic()` is called from here, but the role itself is
 * still written as `"BuildLogic" { }` inside `roles/BuildLogicRole.kt`, so its own declaration
 * site is that file, same as every other role in this project. `DeclarationSiteSpec` checks
 * both halves of that split.
 */
fun DeclarationContainerScope.gradleGroup() = gradle {
    buildLogic()
}
