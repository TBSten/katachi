package com.example.kmp.groups

import com.example.kmp.processor.owner
import me.tbsten.katachi.dsl.DeclarationContainerScope
import me.tbsten.katachi.dsl.gradle.gradle

/**
 * The `"Gradle"` group katachi ships (see [gradle]): the wrapper, `settings.gradle.kts`, every
 * module's `build.gradle.kts`, the root `gradle.properties` and the version catalog.
 *
 * `gradle()` needs no module list: its build-script role expands `":**".module { }`, the same
 * modules `settings.gradle.kts` already names. Its own `"Gradle"` group already sets
 * `documented = false` and a title, so it is called as is rather than wrapped in a group of
 * this sample's own.
 *
 * `owner = "platform"` is set here, on the group rather than on a role -- `owner` reads
 * [com.example.kmp.processor.Owner] through [me.tbsten.katachi.dsl.MetadataScope], which a
 * group is one of. The roles `gradle()` declares (`Gradle/BuildScript`,
 * `Gradle/SettingsScript`, ...) are katachi's own and cannot be reopened from this block to tag
 * each one by hand.
 * Tagging the group once and asking [com.example.kmp.processor.PlatformOwnedFilesProcessor] to
 * also walk a tagged group's roles reaches the same files, and is the shape
 * [me.tbsten.katachi.dsl.MetadataScope]'s own KDoc shows first: `owner` written on a group,
 * `arch.groups.single()[Owner]` read back.
 *
 * Because the group and every role inside it are declared inside katachi's own
 * `GradleGroup.kt`, not here, `Group.declaredAt` / `Role.declaredAt` for all of them resolve to
 * the line below -- katachi walks the stack past its own frames to the first one outside
 * itself, which is this call. `ProjectArchitectureSpec` carves this subtree out of the "one
 * declaration, one file" checks it runs on the rest of the definition, because there genuinely
 * is only one declaration site here: this file.
 */
fun DeclarationContainerScope.gradleGroup() = gradle {
    owner = "platform"
}
