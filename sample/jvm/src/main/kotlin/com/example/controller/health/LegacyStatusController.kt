package com.example.controller.health

import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get

class LegacyStatusController {
    fun register(route: Route) {
        route.get("/status") {
            call.respondText("ok")
        }
    }
}
