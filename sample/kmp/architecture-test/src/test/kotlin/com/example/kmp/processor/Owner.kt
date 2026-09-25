package com.example.kmp.processor

import me.tbsten.katachi.ExperimentalKatachiApi
import me.tbsten.katachi.dsl.MetadataKey
import me.tbsten.katachi.dsl.MetadataScope
import me.tbsten.katachi.dsl.metadata

/**
 * Which team reviews a role's, or a whole group's, files.
 *
 * `owner` is not a word katachi ships. It belongs to this sample alone, brought in exactly the
 * way [MetadataKey]'s own KDoc shows a processor author doing it: a key, and a property to
 * write it through. [PlatformOwnedFilesProcessor] is the only thing that reads it.
 *
 * Written on [MetadataScope] rather than only on `RoleScope`, the same choice [MetadataScope]'s
 * own KDoc walks through: `groups/GradleGroup.kt` tags the whole `"Gradle"` group with
 * `owner = "platform"` because the roles `gradle()` declares underneath it cannot be reopened
 * one by one from a definition outside katachi, the way `tool/Git` still is below. A processor
 * that wants "every file `owner` reaches" therefore has to look at both -- see
 * [PlatformOwnedFilesProcessor].
 *
 * Both declarations need `@OptIn(ExperimentalKatachiApi::class)` because [MetadataKey] and
 * [metadata] are themselves `@ExperimentalKatachiApi`. Writing `owner = "platform"` at a call
 * site does not need the same opt-in, for the same reason `title = "..."` does not: the wall
 * is crossed once, here, where the sugar is defined -- not at every place it is used.
 */
@OptIn(ExperimentalKatachiApi::class)
val Owner: MetadataKey<String> = metadata()

/** Sugar over [Owner], on [MetadataScope] so it reaches a group as well as a role. */
@OptIn(ExperimentalKatachiApi::class)
var MetadataScope.owner: String? by Owner
