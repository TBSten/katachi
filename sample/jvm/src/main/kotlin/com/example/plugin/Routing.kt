package com.example.plugin

import com.example.controller.health.HealthController
import io.ktor.server.application.*
import io.ktor.server.routing.*

fun Application.configureRouting() {
    val healthController = HealthController()
    routing {
        healthController.register(this)
    }
}
