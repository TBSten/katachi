package com.example.plugin

import com.example.controller.HealthController
import io.ktor.server.application.Application
import io.ktor.server.routing.routing

/** Wires every controller into the routing tree. */
fun Application.configureRouting() {
    val healthController = HealthController()
    routing {
        healthController.register(this)
    }
}
