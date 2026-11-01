package com.example.controller.health

import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get

// Deliberately breaks the layout (controller/health/ may hold only HealthController.kt) as the
// baseline() demo. Renaming or deleting it turns its katachi-baseline.json entry into a
// [StaleBaselineEntry] failure.
class LegacyStatusController {
    fun register(route: Route) {
        route.get("/status") {
            call.respondText("ok")
        }
    }
}
