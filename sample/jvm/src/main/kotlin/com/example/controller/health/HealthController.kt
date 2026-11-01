package com.example.controller.health

import com.example.service.HealthService
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get

class HealthController(
    private val healthService: HealthService = HealthService(),
) {
    fun register(route: Route) {
        route.get("/health") {
            call.respond(healthService.currentHealth())
        }
    }
}
