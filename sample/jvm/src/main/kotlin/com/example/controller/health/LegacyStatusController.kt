package com.example.controller.health

import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get

/**
 * The status endpoint from before the controllers were named after their resource, left where
 * it was when katachi was adopted.
 *
 * Deliberately breaks the layout, as the demo of a `[CaptureMismatch]` held back by `baseline()`:
 * the `Controller` role derives the file name from the resource directory
 * (`${capture("resource").pascalCase}Controller`), so `controller/health/` may hold
 * `HealthController.kt` and nothing else named `*Controller.kt`. It is recorded in
 * `katachi-baseline.json` and held back instead of failing the test. Renaming it to
 * `HealthController.kt` (or deleting it) turns its entry stale until the baseline is pruned.
 */
class LegacyStatusController {
    fun register(route: Route) {
        route.get("/status") {
            call.respondText("ok")
        }
    }
}
