@file:OptIn(ExperimentalKatachiApi::class)

package com.example

import me.tbsten.katachi.ExperimentalKatachiApi
import me.tbsten.katachi.dsl.DocumentSection
import me.tbsten.katachi.dsl.MetadataScope
import me.tbsten.katachi.dsl.documentSection
import org.intellij.lang.annotations.Language

/**
 * A heading this project declared for itself: the prose that says what may be placed inside a
 * role or a group.
 *
 * Every role and group in this sample used to spell this out as a paragraph inside its
 * `description`. Pulling it into its own heading keeps `description` to what the layer is and
 * why, and gives "what may be placed here" a place a reader can jump to directly.
 */
val AllowedContents: DocumentSection = documentSection("Allowed contents")

/** Writes the body of [AllowedContents], on a role, a group, or the root of the definition. */
@get:Language("markdown")
@set:Language("markdown")
var MetadataScope.allowedContents: String? by AllowedContents

/**
 * A heading this project declared for itself: the list of what must not be placed inside a role
 * or a group.
 *
 * The sibling of [AllowedContents]. Every role and group in this sample used to spell this out
 * as a bullet list inside its `description`; pulling it into its own heading keeps that list
 * findable on its own, next to what is actually allowed.
 */
val ForbiddenContents: DocumentSection = documentSection("Forbidden contents")

/** Writes the body of [ForbiddenContents], on a role, a group, or the root of the definition. */
@get:Language("markdown")
@set:Language("markdown")
var MetadataScope.forbiddenContents: String? by ForbiddenContents
