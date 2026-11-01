package com.example.repository

import com.example.model.Health

class HealthRepository {
    fun load(): Health = Health(status = "UP", version = "0.1.0")
}
