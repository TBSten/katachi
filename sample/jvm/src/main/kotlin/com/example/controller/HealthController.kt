package com.example.controller

import com.example.service.HealthService
import io.ktor.server.response.*
import io.ktor.server.routing.*

/** Maps `GET /health` onto [HealthService]. */
class HealthController(
    private val healthService: HealthService = HealthService(),
) {
    fun register(route: Route) {
        route.get("/health") {
            call.respond(healthService.currentHealth())
        }
    }
}
