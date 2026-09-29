@file:OptIn(ExperimentalKatachiApi::class)

package com.example.sample

import me.tbsten.katachi.ExperimentalKatachiApi
import me.tbsten.katachi.dsl.MetadataScope
import me.tbsten.katachi.dsl.documentSection
import org.intellij.lang.annotations.Language

/** What may sit in this role's or group's files, kept apart from the free-form [description]. */
val AllowedContents = documentSection("Allowed contents")
@get:Language("markdown")
@set:Language("markdown")
var MetadataScope.allowedContents by AllowedContents

/** What must not sit in this role's or group's files, kept apart from the free-form [description]. */
val ForbiddenContents = documentSection("Forbidden contents")
@get:Language("markdown")
@set:Language("markdown")
var MetadataScope.forbiddenContents by ForbiddenContents
