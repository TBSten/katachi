package com.example.service

import com.example.model.Health
import com.example.repository.HealthRepository

/** Application specific behaviour behind `GET /health`. */
class HealthService(
    private val healthRepository: HealthRepository = HealthRepository(),
) {
    fun currentHealth(): Health = healthRepository.load()
}
