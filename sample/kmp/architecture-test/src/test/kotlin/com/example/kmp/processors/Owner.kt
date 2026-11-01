package com.example.kmp.processors

import me.tbsten.katachi.ExperimentalKatachiApi
import me.tbsten.katachi.dsl.MetadataKey
import me.tbsten.katachi.dsl.MetadataScope
import me.tbsten.katachi.dsl.metadata

@OptIn(ExperimentalKatachiApi::class)
val Owner: MetadataKey<String> = metadata()

@OptIn(ExperimentalKatachiApi::class)
var MetadataScope.owner: String? by Owner
