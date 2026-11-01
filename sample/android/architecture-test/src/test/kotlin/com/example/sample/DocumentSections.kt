@file:OptIn(ExperimentalKatachiApi::class)

package com.example.sample

import me.tbsten.katachi.ExperimentalKatachiApi
import me.tbsten.katachi.dsl.MetadataScope
import me.tbsten.katachi.dsl.documentSection
import org.intellij.lang.annotations.Language

val AllowedContents = documentSection("Allowed contents")
@get:Language("markdown")
@set:Language("markdown")
var MetadataScope.allowedContents by AllowedContents

val ForbiddenContents = documentSection("Forbidden contents")
@get:Language("markdown")
@set:Language("markdown")
var MetadataScope.forbiddenContents by ForbiddenContents
