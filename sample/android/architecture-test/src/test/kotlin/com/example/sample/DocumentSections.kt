@file:OptIn(ExperimentalKatachiApi::class)

package com.example.sample

import me.tbsten.katachi.ExperimentalKatachiApi
import me.tbsten.katachi.dsl.MetadataScope
import me.tbsten.katachi.dsl.documentSection

/** What may sit in this role's or group's files, kept apart from the free-form [description]. */
val AllowedContents = documentSection("置いてよいもの")
var MetadataScope.allowedContents by AllowedContents

/** What must not sit in this role's or group's files, kept apart from the free-form [description]. */
val ForbiddenContents = documentSection("置いてはいけないもの")
var MetadataScope.forbiddenContents by ForbiddenContents
