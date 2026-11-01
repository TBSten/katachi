@file:OptIn(ExperimentalKatachiApi::class)

package com.example

import me.tbsten.katachi.ExperimentalKatachiApi
import me.tbsten.katachi.dsl.DocumentSection
import me.tbsten.katachi.dsl.MetadataScope
import me.tbsten.katachi.dsl.documentSection
import org.intellij.lang.annotations.Language

val AllowedContents: DocumentSection = documentSection("Allowed contents")

@get:Language("markdown")
@set:Language("markdown")
var MetadataScope.allowedContents: String? by AllowedContents

val ForbiddenContents: DocumentSection = documentSection("Forbidden contents")

@get:Language("markdown")
@set:Language("markdown")
var MetadataScope.forbiddenContents: String? by ForbiddenContents
