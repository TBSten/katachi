package com.example.repository

import com.example.model.Health

/**
 * Data source of the health status.
 *
 * A real repository would talk to a database; this sample only has to be shaped like one,
 * because what katachi checks is where the file lives and what it is named.
 */
class HealthRepository {
    fun load(): Health = Health(status = "UP", version = "0.1.0")
}
