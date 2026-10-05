@file:OptIn(ExperimentalKatachiApi::class)

package com.example.kmp

import me.tbsten.katachi.ExperimentalKatachiApi
import me.tbsten.katachi.dsl.MetadataScope
import me.tbsten.katachi.dsl.documentSection
import org.intellij.lang.annotations.Language

/**
 * What a role or group's directory is meant to hold.
 *
 * The prose that explains a layer's purpose lives in `description`; the bare lists of what may
 * and may not sit inside it are pulled out into their own sections instead, so a reader can scan
 * them without wading through the paragraphs around them.
 */
val AllowedContents = documentSection("Allowed contents")
@get:Language("markdown")
@set:Language("markdown")
var MetadataScope.allowedContents by AllowedContents

/** The sibling of [AllowedContents]: what must not be placed in this role or group. */
val ForbiddenContents = documentSection("Forbidden contents")
@get:Language("markdown")
@set:Language("markdown")
var MetadataScope.forbiddenContents by ForbiddenContents
