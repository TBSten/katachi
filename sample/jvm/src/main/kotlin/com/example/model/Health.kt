package com.example.model

import kotlinx.serialization.Serializable

/** Body of `GET /health`. */
@Serializable
data class Health(
    val status: String,
    val version: String,
)
