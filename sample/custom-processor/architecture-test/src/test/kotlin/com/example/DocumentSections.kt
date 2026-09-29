@file:OptIn(ExperimentalKatachiApi::class)

package com.example

import me.tbsten.katachi.ExperimentalKatachiApi
import me.tbsten.katachi.dsl.DocumentSection
import me.tbsten.katachi.dsl.MetadataScope
import me.tbsten.katachi.dsl.documentSection
import org.intellij.lang.annotations.Language

/**
 * A heading this project declared for itself: what may be placed in this role or group.
 *
 * Every role and group here used to spell this out as prose buried in its `description`. Pulling
 * it into a section of its own is what lets a reader find "what goes here" without reading the
 * whole paragraph first, and lets the sibling section [ForbiddenContents] sit right next to it in
 * the generated page.
 */
val AllowedContents: DocumentSection = documentSection("置いてよいもの")

/** Writes the body of [AllowedContents], on a group, a role, or the root of the definition. */
@get:Language("markdown")
@set:Language("markdown")
var MetadataScope.allowedContents: String? by AllowedContents

/**
 * A heading this project declared for itself: what must not be placed in this role or group.
 *
 * The pair to [AllowedContents]. Where the allowed side reads as one paragraph, this one is
 * usually a list of the things a reviewer would otherwise have to remember on their own.
 */
val ForbiddenContents: DocumentSection = documentSection("置いてはいけないもの")

/** Writes the body of [ForbiddenContents], on a group, a role, or the root of the definition. */
@get:Language("markdown")
@set:Language("markdown")
var MetadataScope.forbiddenContents: String? by ForbiddenContents
