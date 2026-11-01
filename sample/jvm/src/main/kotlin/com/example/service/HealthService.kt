package com.example.service

import com.example.model.Health
import com.example.repository.HealthRepository

class HealthService(
    private val healthRepository: HealthRepository = HealthRepository(),
) {
    fun currentHealth(): Health = healthRepository.load()
}
