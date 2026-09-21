package com.example.kmp

import me.tbsten.katachi.dsl.ExperimentalKatachiApi
import me.tbsten.katachi.dsl.MetadataKey
import me.tbsten.katachi.dsl.RoleScope
import me.tbsten.katachi.dsl.metadata

/**
 * Which team reviews a role's files.
 *
 * `owner` is not a word katachi ships. It belongs to this sample alone, brought in exactly the
 * way [MetadataKey]'s own KDoc shows a processor author doing it: a key, and a property to
 * write it through. [PlatformOwnedFilesProcessor] is the only thing that reads it.
 *
 * Both declarations need `@OptIn(ExperimentalKatachiApi::class)` because [MetadataKey] and
 * [metadata] are themselves `@ExperimentalKatachiApi`. Writing `owner = "platform"` at a call
 * site does not need the same opt-in, for the same reason `title = "..."` does not: the wall
 * is crossed once, here, where the sugar is defined -- not at every place it is used.
 */
@OptIn(ExperimentalKatachiApi::class)
val Owner: MetadataKey<String> = metadata()

/** Sugar over [Owner], written the same way katachi writes `RoleScope.title`. */
@OptIn(ExperimentalKatachiApi::class)
var RoleScope.owner: String? by Owner
