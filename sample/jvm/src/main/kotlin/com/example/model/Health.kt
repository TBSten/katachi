package com.example.model

import kotlinx.serialization.Serializable

@Serializable
data class Health(
    val status: String,
    val version: String,
)
